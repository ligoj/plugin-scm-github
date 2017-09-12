package org.ligoj.app.plugin.scm.github;

import java.io.IOException;
import java.text.Format;
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
import org.ligoj.app.resource.NormalizeFormat;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.CurlProcessor;
import org.ligoj.app.resource.plugin.CurlRequest;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
	public boolean checkStatus(final Map<String, String> parameters) throws Exception { // NOSONAR
		final CurlRequest request = new CurlRequest(HttpMethod.GET,
				githubApiUrl + "users/" + parameters.get(PARAMETER_USER) + "/repos", null);
		return processGitHubRequest(request, parameters);
	}

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final Map<String, String> parameters) {
		final SubscriptionStatusWithData nodeStatusWithData = new SubscriptionStatusWithData();
		nodeStatusWithData.put("info", validateRepository(parameters));
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
		final CurlRequest request = new CurlRequest(HttpMethod.GET,
				githubApiUrl + "users/" + parameters.get(PARAMETER_USER) + "/repos", null);
		request.setSaveResponse(true);

		if (processGitHubRequest(request, parameters)) {
			// Prepare the context, an ordered set of projects
			final Format format = new NormalizeFormat();
			final String formatCriteria = format.format(criteria);

			// Map, filter and limit the result
			final ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			final List<NamedBean<String>> result = objectMapper.<List<NamedBean<String>>>readValue(
					request.getResponse(), new TypeReference<List<NamedBean<String>>>() {
					});
			return result.stream().filter(repo -> format.format(repo.getName()).contains(formatCriteria))
					.map(repo -> new NamedBean<>(repo.getName(), repo.getName())).limit(10)
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
