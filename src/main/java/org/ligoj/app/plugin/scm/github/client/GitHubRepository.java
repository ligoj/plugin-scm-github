package org.ligoj.app.plugin.scm.github.client;

import org.ligoj.bootstrap.core.NamedBean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * Github statistics model.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubRepository extends NamedBean<String> {

	/**
	 * Repository open issues
	 */
	@JsonProperty("open_issues")
	private int openIssues;

	/**
	 * Stars count
	 */
	@JsonProperty("stargazers_count")
	private int stargazersCount;

	/**
	 * Watchers count
	 */
	private int watchers;

}
