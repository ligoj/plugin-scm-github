package org.ligoj.app.plugin.scm.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * GitHub contributor model.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubContributor {
	/**
	 * Contributor login
	 */
	private String login;

	/**
	 * Contribution count
	 */
	private int contributions;

	/**
	 * Avatar URL
	 */
	@JsonProperty("avatar_url")
	private String avatarUrl;
}
