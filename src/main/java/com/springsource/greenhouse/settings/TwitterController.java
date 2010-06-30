package com.springsource.greenhouse.settings;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth.consumer.token.OAuthConsumerToken;
import org.springframework.security.oauth.consumer.token.OAuthConsumerTokenServices;
import org.springframework.security.oauth.consumer.token.OAuthConsumerTokenServicesFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.flash.FlashMap;

import com.springsource.greenhouse.oauth.NetworkConnectionsTokenServices;
import com.springsource.greenhouse.signin.GreenhouseUserDetails;

@Controller
@RequestMapping("/settings")
public class TwitterController {
    
	private static final String LINKED_TO_TWITTER_MESSAGE = "Signed up at the Greenhouse at ";

    private OAuthConsumerTokenServicesFactory tokenServicesFactory;
    
    private TwitterService twitterService;
    
    private JdbcTemplate jdbcTemplate;
    
    @Inject
    public TwitterController(OAuthConsumerTokenServicesFactory tokenServicesFactory, TwitterService twitterService, JdbcTemplate jdbcTemplate) {
		this.tokenServicesFactory = tokenServicesFactory;
		this.jdbcTemplate = jdbcTemplate;
		this.twitterService = twitterService;
	}

	@RequestMapping(value="/twitter", method=RequestMethod.GET)
    public String connectView(GreenhouseUserDetails currentUser) {
		if (isConnected(currentUser)) {
			return "settings/twitterConnected";
		} else {
			return "settings/twitterConnect";
		}
	}
    
	@RequestMapping("/twitterconnect/authorize")
    public String authorize(HttpServletRequest request, Authentication authentication, @RequestParam(value="tweetIt", required=false) boolean tweetIt) {
        String oauthToken = request.getParameter("oauth_token");      
        if (oauthToken != null && oauthToken.length() > 0) {
            OAuthConsumerToken accessToken = getAccessToken(request, authentication);

            if(tweetIt) {
                tweetAboutConnection(accessToken, request, authentication);
            }
                        
            FlashMap.getCurrent(request).put("connectedMessage", "Your Twitter account is now linked to your Greenhouse account!");
        }
        return "redirect:/settings/twitter";
    }

    private void tweetAboutConnection(OAuthConsumerToken accessToken, HttpServletRequest request, Authentication authentication) {
        if(accessToken.isAccessToken()) {
            String message = LINKED_TO_TWITTER_MESSAGE + assembleMemberProfileUrl(request, authentication);
            twitterService.updateStatus(accessToken, message);
        }
    }

	private OAuthConsumerToken getAccessToken(HttpServletRequest request,
            Authentication authentication) {
	    OAuthConsumerTokenServices tokenServices = tokenServicesFactory.getTokenServices(authentication, request);
        OAuthConsumerToken accessToken = tokenServices.getToken("twitter");
	    return accessToken;
    }
	
	@RequestMapping(value="/twitter", method=RequestMethod.DELETE)
    public String disconnectTwitter(GreenhouseUserDetails currentUser, HttpServletRequest request, Authentication authentication) {
	    NetworkConnectionsTokenServices tokenServices = (NetworkConnectionsTokenServices) tokenServicesFactory.getTokenServices(authentication, request);
        tokenServices.removeToken("twitter");
		return "redirect:/settings/twitter";
	}

	// internal helpers
	
    private boolean isConnected(GreenhouseUserDetails currentUser) {
    	return jdbcTemplate.queryForInt("select count(*) from NetworkConnection where userId = ? and network = 'twitter'", currentUser.getEntityId()) == 1;
	}
    
    private String assembleMemberProfileUrl(HttpServletRequest request, Authentication authentication) {
        GreenhouseUserDetails user = (GreenhouseUserDetails) authentication.getPrincipal();
        String userKey = user.getProfileKey();
        int serverPort = request.getServerPort();
        String portPart = serverPort == 80 || serverPort == 443 ? "" : ":" + serverPort;
        return request.getScheme() + "://" + request.getServerName() + portPart + request.getContextPath() + "/members/" + userKey;
    }

}