package org.ligoj.app.plugin.scm.github;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.plugin.scm.ScmResource;
import org.ligoj.app.plugin.scm.ScmServicePlugin;
import org.ligoj.app.plugin.scm.github.client.GitHubContributor;
import org.ligoj.app.plugin.scm.github.client.GitHubRepository;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.curl.CurlProcessor;
import org.ligoj.bootstrap.core.curl.CurlRequest;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) throws IOException {
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
	 * @throws IOException When data cannot be read from GitHub.
	 */
	private List<GitHubContributor> getContributorsInformation(final Map<String, String> parameters)
			throws IOException {
		final CurlRequest request = new CurlRequest(HttpMethod.GET, getApiUrl() + "repos/"
				+ parameters.get(PARAMETER_USER) + "/" + parameters.get(PARAMETER_REPO) + "/contributors", null);
		request.setSaveResponse(true);
		processGitHubRequest(request, parameters);
		return objectMapper.readValue(request.getResponse(),
				new TypeReference<List<GitHubContributor>>() {
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
	 * @throws IOException unexpected exception
	 */
	@GET
	@Path("repos/{node}/{criteria}")
	public List<NamedBean<String>> findReposByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria) throws IOException {
		final Map<String, String> parameters = pvResource.getNodeParameters(node);
		final CurlRequest request = new CurlRequest(HttpMethod.GET, getApiUrl() + "search/repositories?per_page=10&q="
				+ criteria + "+user:" + parameters.get(PARAMETER_USER), null);
		request.setSaveResponse(true);
		if (processGitHubRequest(request, parameters)) {
			// Map the result
			final List<GitHubRepository> result = objectMapper.convertValue(
					objectMapper.readTree(request.getResponse()).get("items"),
					new TypeReference<List<GitHubRepository>>() {
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

}
