define(['sparkline'], function () {
	var current = {

		configureSubscriptionParameters: function (configuration) {
			current.$super('registerXServiceSelect2')(configuration, 'service:scm:github:repository', 'service/scm/github/repos/', null, true, null, false);
		},

		/**
		 * Render github repository.
		 */
		renderKey: function (subscription) {
			return current.$super('renderKey')(subscription, 'service:scm:github:repository');
		},

		/**
		 * Render github home page.
		 */
		renderFeatures: function (subscription) {
			subscription.parameters['service:scm:github:url'] = 'https://github.com/' + subscription.parameters['service:scm:github:user'];
			return current.$super('renderFeaturesScm')(subscription, 'github');
		},
		
		/**
		 * Render details features
		 */
		renderDetailsFeatures: function (subscription) {
			window.setTimeout(function () {
				current.pieContributors(subscription);
			}, 50);
			return '<span></span>';
		},
		/**
		 * pie chart of contributions
		 */
		pieContributors: function(subscription) {
			$('[data-subscription="' + subscription.id + '"]').find('.features>span').sparkline(
					subscription.data.contribs.map(function(contrib) { return contrib.contributions; }), {
						type: 'pie',
						tooltipFormatter: function (sparkline, options, fields) {
							return '<img class="github-avatar" src="' + subscription.data.contribs[fields.offset].avatar_url +'"/>'+subscription.data.contribs[fields.offset].login + ': ' + fields.value + ' ' + current.$messages['service:scm:github:contributions'];
						}
					});
		},
		/**
		 * Render github details : id, and amount of revisions.
		 */
		renderDetailsKey: function (subscription) {
			var repo = 'https://github.com/' + subscription.parameters['service:scm:github:user'] + '/' + subscription.parameters['service:scm:github:repository']
			return current.$super('generateCarousel')(subscription, [
				current.renderKey(subscription),
				'     <a href="' + repo + '/issues" target="_blank" data-toggle="tooltip" title="' +  current.$messages['service:scm:github:openissues']  + '"><li class="fas fa-exclamation-circle "/> ' + subscription.data.issues +
				'</a> <a href="' + repo + '" target="_blank"        data-toggle="tooltip" title="' +  current.$messages['service:scm:github:stars']  + '"     ><li class="fas fa-star"/> ' + subscription.data.stars +
				'</a> <a href="' + repo + '" target="_blank"        data-toggle="tooltip" title="' +  current.$messages['service:scm:github:watchers']  + '"  ><li class="fas fa-eye"/> ' + subscription.data.watchers+'</a>'
				], 1);
		}
	};
	return current;
});
