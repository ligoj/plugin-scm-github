define(function () {
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
		 * Render github details : id, and amount of revisions.
		 */
		renderDetailsKey: function (subscription) {
			return current.$super('generateCarousel')(subscription, [current.renderKey(subscription), '#Revisions : ' + subscription.data.info]);
		}
	};
	return current;
});
