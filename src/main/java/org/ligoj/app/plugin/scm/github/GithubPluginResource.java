package org.ligoj.app.plugin.scm.github;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.plugin.scm.ScmResource;
import org.ligoj.app.plugin.scm.ScmServicePlugin;
import org.ligoj.app.plugin.scm.github.client.GitHubContributor;
import org.ligoj.app.plugin.scm.github.client.GitHubRepository;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.CurlProcessor;
import org.ligoj.app.resource.plugin.CurlRequest;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Github resource.
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
	public static final String PARAMETER_USER = KEY + ":user";
	public static final String PARAMETER_REPO = KEY + ":repository";
	public static final String PARAMETER_AUTH_KEY = KEY + ":auth-key";

	/**
	 * github api url
	 */
	private String githubApiUrl = "https://api.github.com/";

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		final CurlRequest request = new CurlRequest(HttpMethod.GET,
				githubApiUrl + "users/" + parameters.get(PARAMETER_USER), null);
		return processGitHubRequest(request, parameters);
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) throws IOException {
		final SubscriptionStatusWithData nodeStatusWithData = new SubscriptionStatusWithData();
		final String repo = validateRepository(parameters);
		final ObjectMapper objectMapper = new ObjectMapper();
		final GitHubRepository result = objectMapper.readValue(repo, GitHubRepository.class);
		nodeStatusWithData.put("issues", result.getOpenIssues());
		nodeStatusWithData.put("stars", result.getStargazersCount());
		nodeStatusWithData.put("watchers", result.getWatchers());
		nodeStatusWithData.put("contribs", getContributorsInformations(parameters));
		return nodeStatusWithData;
	}

	/**
	 * validate a repository defined by input parameters.
	 * 
	 * @param parameters
	 *            subscription parameters
	 */
	private String validateRepository(final Map<String, String> parameters) {
		final CurlRequest request = new CurlRequest(HttpMethod.GET,
				githubApiUrl + "repos/" + parameters.get(PARAMETER_USER) + "/" + parameters.get(PARAMETER_REPO), null);
		request.setSaveResponse(true);
		if (!processGitHubRequest(request, parameters)) {
			throw new ValidationJsonException(PARAMETER_REPO, "github-repository", parameters.get(PARAMETER_REPO));
		}
		return request.getResponse();
	}

	/**
	 * validate a repository defined by input parameters.
	 * 
	 * @param parameters
	 *            subscription parameters
	 * @throws IOException
	 */
	private List<GitHubContributor> getContributorsInformations(final Map<String, String> parameters)
			throws IOException {
		final CurlRequest request = new CurlRequest(HttpMethod.GET, githubApiUrl + "repos/"
				+ parameters.get(PARAMETER_USER) + "/" + parameters.get(PARAMETER_REPO) + "/contributors", null);
		request.setSaveResponse(true);
		processGitHubRequest(request, parameters);
		return new ObjectMapper().<List<GitHubContributor>>readValue(request.getResponse(),
				new TypeReference<List<GitHubContributor>>() {
				});
	}

	@Override
	public void link(int subscription) {
		validateRepository(pvResource.getSubscriptionParameters(subscription));
	}

	/**
	 * Return user's repositories filtered by name.
	 * 
	 * @param node
	 *            the node used to retrieve parameters needed to find
	 *            repositories
	 * @param criteria
	 *            search criteria
	 * @return user's repositories
	 * @throws IOException
	 *             unexpected exception
	 */
	@GET
	@Path("repos/{node}/{criteria}")
	public List<NamedBean<String>> findReposByName(@PathParam("node") final String node,
			@PathParam("criteria") final String criteria) throws IOException {
		final Map<String, String> parameters = pvResource.getNodeParameters(node);
		final CurlRequest request = new CurlRequest(HttpMethod.GET, githubApiUrl + "search/repositories?per_page=10&q="
				+ criteria + "+user:" + parameters.get(PARAMETER_USER), null);
		request.setSaveResponse(true);
		if (processGitHubRequest(request, parameters)) {
			// Map the result
			final ObjectMapper objectMapper = new ObjectMapper();
			final List<GitHubRepository> result = objectMapper.convertValue(
					objectMapper.readTree(request.getResponse()).get("items"),
					new TypeReference<List<GitHubRepository>>() {
					});
			return result.stream().map(repo -> new NamedBean<>(repo.getName(), repo.getName()))
					.collect(Collectors.toList());
		}
		return new ArrayList<>();
	}

	/**
	 * execute a curl request to GitHub with authentication.
	 * 
	 * @param request
	 *            curl request
	 * @param parameters
	 *            subscription parameters
	 * @return true id request succeed
	 */
	private boolean processGitHubRequest(final CurlRequest request, final Map<String, String> parameters) {
		request.getHeaders().put("Authorization", "token " + parameters.get(PARAMETER_AUTH_KEY));
		return new CurlProcessor().process(request);
	}

}
