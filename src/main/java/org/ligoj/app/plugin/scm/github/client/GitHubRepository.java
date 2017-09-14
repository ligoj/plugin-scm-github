package org.ligoj.app.plugin.scm.github.client;

import org.ligoj.bootstrap.core.NamedBean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepository extends NamedBean<String> {

	/**
	 * repo open issues
	 */
	@JsonProperty("open_issues")
	private int openIssues;

	/**
	 * stars count
	 */
	@JsonProperty("stargazers_count")
	private int stargazersCount;

	/**
	 * watchers count
	 */
	private int watchers;

}
