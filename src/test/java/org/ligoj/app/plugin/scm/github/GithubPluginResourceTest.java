package org.ligoj.app.plugin.scm.github;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.dao.ParameterValueRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.scm.github.client.GitHubContributor;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link GithubPluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class GithubPluginResourceTest extends AbstractServerTest {
	@Autowired
	private GithubPluginResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private ParameterValueRepository parameterValueRepository;
	@Autowired
	private ConfigurationResource configuration;

	protected int subscription;

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistEntities("csv",
				new Class[] { Node.class, Parameter.class, Project.class, Subscription.class, ParameterValue.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");
		// Override the API URL pointing to the mock server
		configuration.put("service:scm:github:api-url", "http://localhost:" + MOCK_PORT + "/");

		// Coverage only
		resource.getKey();
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is only one subscription for a service.
	 */
	private Integer getSubscription(final String project) {
		return getSubscription(project, GithubPluginResource.KEY);
	}

	@Test
	void delete() throws Exception {
		resource.delete(subscription, false);
		em.flush();
		em.clear();
		// No custom data -> nothing to check;
	}

	@Test
	void getVersion() throws Exception {
		Assertions.assertNull(resource.getVersion(subscription));
	}

	@Test
	void getLastVersion() throws Exception {
		Assertions.assertNull(resource.getLastVersion());
	}

	@Test
	void link() throws Exception {
		prepareMockRepoDetail();
		httpServer.start();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		resource.link(this.subscription);

		// Nothing to validate for now...
	}

	@Test
	void linkNotFound() throws Exception {
		prepareMockUser();
		httpServer.start();

		parameterValueRepository.findAllBySubscription(subscription).stream()
				.filter(v -> v.getParameter().getId().equals(GithubPluginResource.KEY + ":repository")).findFirst()
				.get().setData("0");
		em.flush();
		em.clear();

		// Invoke create for an already created entity, since for now, there is
		// nothing but validation pour SonarQube
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.link(this.subscription);
		}), "service:scm:github:repository", "github-repository");
	}

	@SuppressWarnings("unchecked")
	@Test
	void checkSubscriptionStatus() throws IOException {
		prepareMockRepoDetail();
		prepareMockContributors();
		final SubscriptionStatusWithData nodeStatusWithData = resource
				.checkSubscriptionStatus(subscriptionResource.getParametersNoCheck(subscription));
		Assertions.assertTrue(nodeStatusWithData.getStatus().isUp());
		Assertions.assertEquals(3, nodeStatusWithData.getData().get("watchers"));
		Assertions.assertEquals(3, nodeStatusWithData.getData().get("stars"));
		Assertions.assertEquals(2, nodeStatusWithData.getData().get("issues"));
		final List<GitHubContributor> contribs = (List<GitHubContributor>) nodeStatusWithData.getData().get("contribs");
		Assertions.assertEquals(3, contribs.size());
		Assertions.assertEquals("fabdouglas", contribs.get(0).getLogin());
		Assertions.assertEquals(345, contribs.get(0).getContributions());
		Assertions.assertEquals("https://avatars1.githubusercontent.com/u/579170?v=4", contribs.get(0).getAvatarUrl());
	}

	private void prepareMockRepoDetail() throws IOException {
		httpServer.stubFor(
				get(urlPathEqualTo("/repos/junit/ligoj-gstack")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/scm/github/repo-detail.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockContributors() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/repos/junit/ligoj-gstack/contributors"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/scm/github/contribs.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockUser() throws IOException {
		httpServer.stubFor(get(urlPathEqualTo("/users/junit")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
				.withBody(IOUtils.toString(new ClassPathResource("mock-server/scm/github/user.json").getInputStream(),
						StandardCharsets.UTF_8))));
		httpServer.start();
	}

	private void prepareMockRepoSearch() throws IOException {
		httpServer
				.stubFor(get(urlPathEqualTo("/search/repositories")).willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toString(
								new ClassPathResource("mock-server/scm/github/search.json").getInputStream(),
								StandardCharsets.UTF_8))));
		httpServer.start();
	}

	@Test
	void checkStatus() throws Exception {
		prepareMockUser();
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void checkStatusBadRequest() {
		httpServer.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		httpServer.start();
		Assertions.assertFalse(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void findReposByName() throws IOException {
		prepareMockRepoSearch();
		httpServer.start();

		final List<NamedBean<String>> projects = resource.findReposByName("service:scm:github:dig", "plugin-");
		Assertions.assertEquals(10, projects.size());
		Assertions.assertEquals("plugin-storage-owncloud", projects.get(0).getId());
		Assertions.assertEquals("plugin-storage-owncloud", projects.get(0).getName());
	}

	@Test
	void findReposByNameNoListing() throws IOException {
		httpServer.start();

		final List<NamedBean<String>> projects = resource.findReposByName("service:scm:github:dig", "as-");
		Assertions.assertEquals(0, projects.size());
	}

}
