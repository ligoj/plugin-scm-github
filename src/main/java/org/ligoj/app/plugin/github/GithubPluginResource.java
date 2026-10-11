package org.ligoj.app.plugin.github;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.plugin.github.client.GitHubContributor;
import org.ligoj.app.plugin.github.client.GitHubRepository;
import org.ligoj.app.plugin.scm.ScmResource;
import org.ligoj.app.plugin.scm.ScmServicePlugin;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.curl.DefaultHttpResponseCallback;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;


/**
 * GitHub resource.
 */
@Path(GithubPluginResource.URL)
@Component
@Produces(MediaType.APPLICATION_JSON)
public class GithubPluginResource extends AbstractToolPluginResource implements ScmServicePlugin {

	/**
	 * Plug-in key.
	 */
	public static final String URL = ScmResource.SERVICE_URL + "/github";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);
	/**
	 * parameters
	 */
	private static final String PARAMETER_USER = KEY + ":user";
	private static final String PARAMETER_REPO = KEY + ":repository";
	private static final String PARAMETER_AUTH_KEY = KEY + ":auth-key";

	/**
	 * Configuration key used for API URL
	 */
	public static final String CONF_API_URL = KEY + ":api-url";

	/**
	 * Default API URL. May be changed to private repositories.
	 */
	public static final String DEFAULT_API_URL = "https://api.github.com/";

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ConfigurationResource configuration;

	/**
	 * Return the API URL for this plug-in.
	 */
	private String getApiUrl() {
		return configuration.get(CONF_API_URL, DEFAULT_API_URL);
	}

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		final CurlRequest request = new CurlRequest(HttpMethod.GET,
				getApiUrl() + "users/" + parameters.get(PARAMETER_USER), null);
		return processGitHubRequest(request, parameters);
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) {
		final SubscriptionStatusWithData nodeStatusWithData = new SubscriptionStatusWithData();
		final String repo = validateRepository(parameters);
		final GitHubRepository result = objectMapper.readValue(repo, GitHubRepository.class);
		nodeStatusWithData.put("issues", result.getOpenIssues());
		nodeStatusWithData.put("stars", result.getStargazersCount());
		nodeStatusWithData.put("watchers", result.getWatchers());
		nodeStatusWithData.put("contribs", getContributorsInformation(parameters));
		return nodeStatusWithData;
	}

	/**
	 * validate a repository defined by input parameters.
	 *
	 * @param parameters subscription parameters
	 */
	private String validateRepository(final Map<String, String> parameters) {
		final CurlRequest request = new CurlRequest(HttpMethod.GET,
				getApiUrl() + "repos/" + parameters.get(PARAMETER_USER) + "/" + parameters.get(PARAMETER_REPO), null);
		request.setSaveResponse(true);
		if (!processGitHubRequest(request, parameters)) {
			throw new ValidationJsonException(PARAMETER_REPO, "github-repository", parameters.get(PARAMETER_REPO));
		}
		return request.getResponse();
	}

	/**
	 * Validate a repository defined by input parameters.
	 *
	 * @param parameters subscription parameters
	 */
	private List<GitHubContributor> getContributorsInformation(final Map<String, String> parameters) {
		final CurlRequest request = new CurlRequest(HttpMethod.GET, getApiUrl() + "repos/"
				+ parameters.get(PARAMETER_USER) + "/" + parameters.get(PARAMETER_REPO) + "/contributors", null);
		request.setSaveResponse(true);
		processGitHubRequest(request, parameters);
		return objectMapper.readValue(request.getResponse(),
				new TypeReference<>() {
					// Nothing to extend
				});
	}

	@Override
	public void link(int subscription) {
		validateRepository(pvResource.getSubscriptionParameters(subscription));
	}

	/**
	 * Return user's repositories filtered by name.
	 *
	 * @param node     the node used to retrieve parameters needed to find repositories
	 * @param criteria search criteria
	 * @return user's repositories
	 */
	@GET
	@Path("repos/{node}/{criteria}")
	public List<NamedBean<String>> findReposByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria) {
		final Map<String, String> parameters = pvResource.getNodeParameters(node);
		final CurlRequest request = new CurlRequest(HttpMethod.GET, getApiUrl() + "search/repositories?per_page=10&q="
				+ criteria + "+user:" + parameters.get(PARAMETER_USER), null);
		request.setSaveResponse(true);
		if (processGitHubRequest(request, parameters)) {
			// Map the result
			final List<GitHubRepository> result = objectMapper.convertValue(
					objectMapper.readTree(request.getResponse()).get("items"),
					new TypeReference<>() {
						// Nothing to extend
					});
			return result.stream().map(repo -> new NamedBean<>(repo.getName(), repo.getName())).toList();
		}
		return Collections.emptyList();
	}

	/**
	 * Execute a CURL request to GitHub with authentication.
	 *
	 * @param request    CURL request
	 * @param parameters Subscription parameters.
	 * @return <code>true</code> when request succeed.
	 */
	private boolean processGitHubRequest(final CurlRequest request, final Map<String, String> parameters) {
		request.getHeaders().put("Authorization", "token " + parameters.get(PARAMETER_AUTH_KEY));
		try (CurlProcessor curl = new CurlProcessor()) {
			return curl.process(request);
		}
	}

	/**
	 * Response header of the GitHub API giving the expiry of the token used by the request. Absent for a token without
	 * expiry.
	 */
	static final String EXPIRATION_HEADER = "GitHub-Authentication-Token-Expiration";

	/**
	 * Response header of GitHub Enterprise Server, which may not send {@link #EXPIRATION_HEADER} at all.
	 */
	static final String ENTERPRISE_HEADER = "X-GitHub-Enterprise-Version";

	/**
	 * Formats of {@link #EXPIRATION_HEADER}: <code>2026-11-30 12:00:00 UTC</code> or <code>2026-11-30 13:00:00 +0100</code>.
	 */
	private static final List<DateTimeFormatter> EXPIRATION_FORMATS = List.of(
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.ENGLISH),
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.ENGLISH));

	/**
	 * Return the expiry of the token of this node. Overrides <code>ToolPlugin#getCredentialExpiry</code> of an API
	 * having the credential expiry, without <code>@Override</code>: this plug-in still builds and runs on an older API,
	 * which never calls it.
	 *
	 * @param node       The node identifier.
	 * @param parameters The node parameters.
	 * @return The expiry, {@link Instant#MAX} for a token without expiry, <code>null</code> when unknown, such as a
	 *         GitHub Enterprise Server not sending the expiry.
	 */
	public Instant getCredentialExpiry(final String node, final Map<String, String> parameters) {
		// The same authenticated call as the status check, reading the expiry header
		final var expiration = new AtomicReference<String>();
		final var enterprise = new AtomicReference<>(false);
		final var callback = new DefaultHttpResponseCallback() {
			@Override
			public boolean onResponse(final CurlRequest request, final ClassicHttpResponse response) throws IOException {
				Optional.ofNullable(response.getFirstHeader(EXPIRATION_HEADER)).map(Header::getValue).ifPresent(expiration::set);
				enterprise.set(response.containsHeader(ENTERPRISE_HEADER));
				return super.onResponse(request, response);
			}
		};
		final var request = new CurlRequest(HttpMethod.GET, getApiUrl() + "users/" + parameters.get(PARAMETER_USER), null,
				callback);
		if (!processGitHubRequest(request, parameters)) {
			// Rejected token: unknown expiry
			return null;
		}
		if (expiration.get() == null) {
			// GitHub tells a token without expiry by the absence of the header, GitHub Enterprise Server may not tell
			return Boolean.TRUE.equals(enterprise.get()) ? null : Instant.MAX;
		}
		return parseExpiration(expiration.get());
	}

	/**
	 * Parse a value of {@link #EXPIRATION_HEADER}.
	 *
	 * @param value The header value.
	 * @return The expiry instant, <code>null</code> when the format is unknown.
	 */
	static Instant parseExpiration(final String value) {
		for (final var format : EXPIRATION_FORMATS) {
			try {
				return ZonedDateTime.parse(value.trim(), format).toInstant();
			} catch (final DateTimeParseException _) {
				// Try the next format
			}
		}
		return null;
	}
}
