package org.w3.ldp.testsuite.test;

import static org.testng.Assert.assertTrue;
import static org.w3.ldp.testsuite.matcher.HttpStatusSuccessMatcher.isSuccessful;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.Link;

import org.apache.commons.lang3.StringUtils;
import org.apache.marmotta.commons.vocabulary.LDP;
import org.jboss.resteasy.plugins.delegates.LinkDelegate;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.w3.ldp.testsuite.LdpTestSuite;
import org.w3.ldp.testsuite.http.HttpHeaders;
import org.w3.ldp.testsuite.http.LdpPreferences;
import org.w3.ldp.testsuite.http.MediaTypes;
import org.w3.ldp.testsuite.mapper.RdfObjectMapper;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.ResourceUtils;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.RDF;
import com.jayway.restassured.response.Header;
import com.jayway.restassured.response.Response;
import com.jayway.restassured.specification.RequestSpecification;

public abstract class LdpTest implements HttpHeaders, MediaTypes, LdpPreferences {
	public final static String HTTP_LOG_FILENAME = "http.log";

	/**
	 * Alternate content to use on POST requests
	 */
	private static Model postModel;

	/**
	 * For HTTP details on validation failures
	 */
	protected PrintStream httpLog;

	/**
	 * Builds a model from a turtle representation in a file
	 * @param path
	 */
	protected Model readModel(String path) {
		Model model = null;
		if (path != null) {
			model = ModelFactory.createDefaultModel();
			InputStream  inputStream = getClass().getClassLoader().getResourceAsStream(path);

			String fakeUri = "http://w3c.github.io/ldp-testsuite/fakesubject";
			// Even though null relative URIs are used in the resource representation file,
			// the resulting model doesn't keep them intact. They are changed to "file://..." if
			// an empty string is passed as base to this method.
			model.read(inputStream, fakeUri, "TURTLE");

			// At this point, the model should contain a resource named
			// "http://w3c.github.io/ldp-testsuite/fakesubject" if
			// there was a null relative URI in the resource representation
			// file.
			Resource subject = model.getResource(fakeUri);
			if (subject != null) {
				ResourceUtils.renameResource(subject, "");
			}

			try {
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return model;
	}

	/**
	 * Initialization of generic resource model. This will run only once
	 * at the beginning of the test suite, so postModel static field
	 * will be assigned once too.
	 *
	 * @param postTtl the resource with Turtle content to use for POST requests
	 * @param httpLogging whether to log HTTP request and response details on errors
	 */
	@BeforeSuite(alwaysRun = true)
	@Parameters({"postTtl", "httpLogging"})
	public void setup(@Optional String postTtl, @Optional String httpLogging) {
		postModel = readModel(postTtl);
		if ("true".equals(httpLogging)) {
			File dir = new File(LdpTestSuite.OUTPUT_DIR);
			dir.mkdirs();
			File file = new File(dir, HTTP_LOG_FILENAME);
			try {
				httpLog = new PrintStream(file);

				// Add the date to the top of the log
				DateFormat df = DateFormat.getDateTimeInstance();
				httpLog.println("LDP Test Suite: HTTP Log (" + df.format(new Date()) + ")");
				httpLog.println();
			} catch (IOException e) {
				System.err.println("WARNING: Error creating http.log for detailed errors");
				e.printStackTrace();
			}
		}
	}

	@AfterSuite(alwaysRun = true)
	public void tearDown() {
		if (httpLog != null) {
			httpLog.close();
		}
	}

	/**
	 * An absolute requirement of the specification.
	 *
	 * @see <a href="https://www.ietf.org/rfc/rfc2119.txt">RFC 2119</a>
	 */
	public static final String MUST = "MUST";

	/**
	 * There may exist valid reasons in particular circumstances to ignore a
	 * particular item, but the full implications must be understood and
	 * carefully weighed before choosing a different course.
	 *
	 * @see <a href="https://www.ietf.org/rfc/rfc2119.txt">RFC 2119</a>
	 */
	public static final String SHOULD = "SHOULD";

	/**
	 * An item is truly optional. One vendor may choose to include the item
	 * because a particular marketplace requires it or because the vendor feels
	 * that it enhances the product while another vendor may omit the same item.
	 *
	 * @see <a href="https://www.ietf.org/rfc/rfc2119.txt">RFC 2119</a>
	 */
	public static final String MAY = "MAY";


	/**
	 * A grouping of tests that may not need to run as part of the regular
	 * TestNG runs.  Though by including it, it will allow for the generation
	 * via various reporters.
	 */
	public static final String MANUAL = "MANUAL";


	private static boolean warnings = false;

	public static boolean getWarnings() {
		return warnings;
	}

	/**
	 * If true, log HTTP request and response details on errors.
	 */
	protected boolean httpLogging = false;

	/**
	 * Build a base RestAssured {@link com.jayway.restassured.specification.RequestSpecification}.
	 *
	 * @return RestAssured Request Specification
	 */
	protected abstract RequestSpecification buildBaseRequestSpecification();

	public Model getAsModel(String uri) {
		return getResourceAsModel(uri, TEXT_TURTLE);
	}

	public Model getResourceAsModel(String uri, String mediaType) {
		return buildBaseRequestSpecification()
				.header(ACCEPT, mediaType)
			.expect()
				.statusCode(isSuccessful())
			.when()
				.get(uri).as(Model.class, new RdfObjectMapper(uri));
	}

	protected Model getDefaultModel() {
		Model model = ModelFactory.createDefaultModel();
		Resource resource = model.createResource("",
				model.createResource("http://example.com/ns#Bug"));
		resource.addProperty(RDF.type, model.createResource(LDP.RDFSource.stringValue()));
		resource.addProperty(
				model.createProperty("http://example.com/ns#severity"), "High");
		resource.addProperty(DCTerms.title, "Another bug to test.");
		resource.addProperty(DCTerms.description, "Issues that need to be fixed.");

		return model;
	}

	protected Model postContent() {
		return postModel != null? postModel : getDefaultModel();
	}

	/**
	 * Are there any restrictions on content when creating resources? This is
	 * assumed to be true if POST content was provided using the {@code postTtl}
	 * test parameter.
	 *
	 * <p>
	 * This method is used for
	 * {@link CommonContainerTest#testRelativeUriResolutionPost()}.
	 * </p>
	 *
	 * @return true if there are restrictions on what triples are allowed; false
	 *		   if the server allows most any RDF
	 * @see #setup(String)
	 * @see RdfSourceTest#restrictionsOnTestResourceContent()
	 */
	protected boolean restrictionsOnPostContent() {
		return postModel != null;
	}

	/**
	 * Tests if a Link response header with the expected URI and relation is
	 * present in an HTTP response. Does not resolve relative URIs. If a Link
	 * URI might be relative, use {@link #containsLinkHeader(String, String,
	 * String, Response)}.
	 *
	 * @param expectedUri
	 *			  the expected URI
	 * @param expectedRel
	 *			  the expected link relation (rel)
	 * @param response
	 *			  the HTTP response
	 * @see <a href="http://tools.ietf.org/html/rfc5988">RFC 5988</a>
	 * @see #getFirstLinkForRelation(String, String, Response)
	 */
	protected boolean containsLinkHeader(String expectedUri, String expectedRel, Response response) {
		return containsLinkHeader(expectedUri, expectedRel, null, response);
	}

	/**
	 * Tests if a Link response header with the expected URI and relation is
	 * present in an HTTP response. Resolves relative URIs against the request
	 * URI if necessary.
	 *
	 * @param expectedLinkUri
	 *			  the expected URI
	 * @param expectedRel
	 *			  the expected link relation (rel)
	 * @param requestUri
	 *			  the HTTP request URI (for resolving relative URIs)
	 * @param response
	 *			  the HTTP response
	 * @see <a href="http://tools.ietf.org/html/rfc5988">RFC 5988</a>
	 * @see #getFirstLinkForRelation(String, String, Response)
	 */
	protected boolean containsLinkHeader(String expectedLinkUri, String expectedRel, String requestUri, Response response) {
		List<Header> linkHeaders = response.getHeaders().getList(LINK);
		for (Header linkHeader : linkHeaders) {
			for (String s : splitLinks(linkHeader)) {
				Link nextLink = new LinkDelegate().fromString(s);
				if (expectedRel.equals(nextLink.getRel())) {
					String actualLinkUri = resolveIfRelative(requestUri, nextLink.getUri());
					if (expectedLinkUri.equals(actualLinkUri)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Gets the first link from {@code response} with link relation {@code rel}.
	 * Resolves relative URIs against the request URI if necessary.
	 *
	 * @param rel
	 *			  the expected link relation
	 * @param requestUri
	 *			  the HTTP request URI (for resolving relative URIs)
	 * @param response
	 *			  the HTTP response
	 * @return the first link or {@code null} if none was found
	 * @see <a href="http://tools.ietf.org/html/rfc5988">RFC 5988</a>
	 * @see #containsLinkHeader(String, String, Response)
	 */
	protected String getFirstLinkForRelation(String rel, String requestUri, Response response) {
		List<Header> linkHeaders = response.getHeaders().getList(LINK);
		for (Header header : linkHeaders) {
			for (String s : splitLinks(header)) {
				Link l = new LinkDelegate().fromString(s);
				if (rel.equals(l.getRel())) {
					return resolveIfRelative(requestUri, l.getUri());
				}
			}
		}

		return null;
	}

	/**
	 * Splits an HTTP Link header that might have multiple links separated by a
	 * comma.
	 *
	 * @param linkHeader
	 *			the link header
	 * @return the list of link-values as defined in RFC 5988 (for example,
	 *		 {@code "<http://example.com/bt/bug432>; rel=related"})
	 * @see <a href="http://tools.ietf.org/html/rfc5988#page-7">RFC 5988: The Link Header Field</a>
	 */
	// LinkDelegate doesn't handle this for us
	protected List<String> splitLinks(Header linkHeader) {
		final ArrayList<String> links = new ArrayList<>();
		final String value = linkHeader.getValue();

		// Track the beginning index for the current link-value.
		int beginIndex = 0;

		// Is the current char inside a URI-Reference?
		boolean inUriRef = false;

		// Split the string on commas, but only if not in a URI-Reference
		// delimited by angle brackets.
		for (int i = 0; i < value.length(); ++i) {
			final char c = value.charAt(i);

			if (c == ',' && !inUriRef) {
				// Found a comma not in a URI-Reference. Split the string.
				final String link = value.substring(beginIndex, i).trim();
				links.add(link);

				// Assign the next begin index for the next link.
				beginIndex = i + 1;
			} else if (c == '<') {
				// Angle brackets are not legal characters in a URI, so they can
				// only be used to mark the start and end of a URI-Reference.
				// See http://tools.ietf.org/html/rfc3986#section-2
				inUriRef = true;
			} else if (c == '>') {
				inUriRef = false;
			}
		}

		// There should be one more link in the string.
		final String link = value.substring(beginIndex, value.length()).trim();
		links.add(link);

		return links;
	}

	/**
	 * Asserts the response has a <code>Preference-Applied:
	 * return=representation</code> response header, but only if at
	 * least one <code>Preference-Applied</code> header is present.
	 *
	 * @param response
	 *			  the HTTP response
	 */
	protected void checkPreferenceAppliedHeader(Response response) {
		List<Header> preferenceAppliedHeaders = response.getHeaders().getList(PREFERNCE_APPLIED);
		if (preferenceAppliedHeaders.isEmpty()) {
			// The header is not mandatory.
			return;
		}

		assertTrue(hasReturnRepresentation(preferenceAppliedHeaders),
				"Server responded with a Preference-Applied header, but it did not contain return=representation");
	}

	protected boolean hasReturnRepresentation(List<Header> preferenceAppliedHeaders) {
		for (Header h : preferenceAppliedHeaders) {
			// Handle optional whitespace, quoted preference token values, and
			// other tokens in the Preference-Applied response header.
			if (h.getValue().matches("(^|.*[ ;])return *= *\"?representation\"?($|[ ;].*)")) {
				return true;
			}
		}

		return false;
	}

	public static String include(String... preferences) {
		return ldpPreference(PREFERENCE_INCLUDE, preferences);
	}

	public static String omit(String... preferences) {
	   return ldpPreference(PREFERENCE_OMIT, preferences);
	}

	private static String ldpPreference(String name, String... values) {
		return "return=representation; " + name + "=\"" + StringUtils.join(values, " ") + "\"";
	}

	/**
	 * Resolves a URI if it's a relative path.
	 *
	 * @param base
	 *			  the base URI to use
	 * @param toResolve
	 *			  a URI that might be relative
	 * @return the resolved URI
	 * @throws URISyntaxException
	 *			   on bad URIs (but relative URIs are OK)
	 */
	public static String resolveIfRelative(String base, String toResolve) {
		try {
			// The URI constructor accepts relative paths
			return resolveIfRelative(base, new URI(toResolve));
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}

	protected static String resolveIfRelative(String base, URI toResolve) {
		if (toResolve.isAbsolute()) {
			return toResolve.toString();
		}

		try {
			return new URI(base).resolve(toResolve).toString();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException(e);
		}
	}
}
