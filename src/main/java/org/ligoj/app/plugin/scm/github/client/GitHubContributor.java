package org.ligoj.app.plugin.scm.github.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * GitHub contributor
 * 
 * @author alocquet
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubContributor {
	/**
	 * contributor login
	 */
	private String login;
	/**
	 * conribution count
	 */
	private int contributions;
}
