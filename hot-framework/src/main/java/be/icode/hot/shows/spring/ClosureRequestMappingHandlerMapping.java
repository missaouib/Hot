package be.icode.hot.shows.spring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import be.icode.hot.shows.ClosureRequestMapping;
import be.icode.hot.shows.Show;
import be.icode.hot.shows.ShowsContext;
import be.icode.hot.spring.config.event.RestRegistrationEvent;

public class ClosureRequestMappingHandlerMapping extends RequestMappingHandlerMapping implements  ApplicationListener<RestRegistrationEvent> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ClosureRequestMappingHandlerMapping.class);

	ShowsContext showsContext;
	
	private final Map<RequestMappingInfo, ClosureRequestMapping> closureMap = new LinkedHashMap<>();
	
	private final Map<Show<?, ?>, List<RequestMappingInfo>> showRequestMappingInfosMap = new LinkedHashMap<>();
	
	private final MultiValueMap<String, RequestMappingInfo> urlMap = new LinkedMultiValueMap<>();
	
	RequestMappingMvcRequestMappingAdapter requestMappingMvcRequestMappingAdapter = new RequestMappingMvcRequestMappingAdapter();
	
	public ClosureRequestMappingHandlerMapping(ShowsContext showsContext) {
		this.showsContext = showsContext;
		setUrlDecode(false);
	}

	public ClosureRequestMapping lookupRequestMapping (HttpServletRequest request) throws Exception {
		
		String lookupPath = getUrlPathHelper().getLookupPathForRequest(request);
		if (logger.isDebugEnabled()) {
			logger.debug("Looking up handler method for path " + lookupPath);
		}
		
		List<Match> matches = new ArrayList<Match>();

		List<RequestMappingInfo> directPathMatches = this.urlMap.get(lookupPath);
		if (directPathMatches != null) {
			addMatchingMappings(directPathMatches, matches, request);
		}

		if (matches.isEmpty()) {
			// No choice but to go through all mappings
			addMatchingMappings(this.closureMap.keySet(), matches, request);
		}

		if (!matches.isEmpty()) {
			Comparator<Match> comparator = new MatchComparator(getMappingComparator(request));
			Collections.sort(matches, comparator);

			if (logger.isTraceEnabled()) {
				logger.trace("Found " + matches.size() + " matching mapping(s) for [" + lookupPath + "] : " + matches);
			}

			Match bestMatch = matches.get(0);
			if (matches.size() > 1) {
				Match secondBestMatch = matches.get(1);
				if (comparator.compare(bestMatch, secondBestMatch) == 0) {
					if (logger.isDebugEnabled()) {
						logger.debug("Ambiguous closure mapped for HTTP path '" + request.getRequestURL() + "'");
					}
				}
			}
			handleMatch(bestMatch.mapping, lookupPath, request);
			return bestMatch.closureRequestMapping;
		} else {
			handleNoMatch(closureMap.keySet(), lookupPath, request);
			return null;
		}
	}
	
	/**
	 * Need to override for avoiding reinitialization of the context
	 */
	@Override
	protected void initApplicationContext(ApplicationContext context) {
		registerShowClosures();
	}
	
	private void addMatchingMappings(Collection<RequestMappingInfo> mappings, List<Match> matches, HttpServletRequest request) {
		for (RequestMappingInfo mapping : mappings) {
			RequestMappingInfo match = getMatchingMapping(mapping, request);
			if (match != null) {
				matches.add(new Match(match, closureMap.get(mapping)));
			}
		}
	}
	
	private void registerShowClosures () {
		closureMap.clear();
		for (Show<?,?> show : showsContext.getShows()) {
			registerShowClosure(show);
		}
	}
	
	private void registerShowClosure (Show<?, ?> show, boolean overwriteExisting) {
		
		List<RequestMappingInfo> showRequestMappingInfos = new ArrayList<>();
		for (ClosureRequestMapping requestMapping : show.getRest().getRequestMappings()) {
			RequestMappingInfo requestMappingInfo = requestMappingMvcRequestMappingAdapter.getRequestMappingInfo(requestMapping);
			if (closureMap.get(requestMappingInfo) != null) {
				if (overwriteExisting) {
					if (LOGGER.isDebugEnabled()) LOGGER.debug("Updating already registered show "+requestMapping.getPaths());
					closureMap.put(requestMappingInfo, requestMapping);
					showRequestMappingInfos.add(requestMappingInfo);
					continue;
				} else {
					if (LOGGER.isDebugEnabled()) LOGGER.debug("Closure ignored, already registered closure for " + requestMappingInfo.toString());
				}
			} else {
				if (LOGGER.isDebugEnabled()) LOGGER.debug("Registering "+requestMapping.getPaths() + " Headers:["+requestMapping.getHeaders()+"]");
				closureMap.put(requestMappingInfo, requestMapping);
				showRequestMappingInfos.add(requestMappingInfo);
			}
			Set<String> patterns = getMappingPathPatterns(requestMappingInfo);
			for (String pattern : patterns) {
				if (!getPathMatcher().isPattern(pattern)) {
					this.urlMap.add(pattern, requestMappingInfo);
				}
			}
		}
		showRequestMappingInfosMap.put(show, showRequestMappingInfos);
	}
	
	private void registerShowClosure (Show<?, ?> show) {
		registerShowClosure(show, false);
	}
	
	/**
	 * Unregister rest paths not defined in show
	 * 
	 * @param show
	 */
	private void unregisterDeletedShowClosure(Show<?, ?> show) {
		
		for (RequestMappingInfo requestMappingInfo : showRequestMappingInfosMap.get(show)) {
			if (!show.getRest().getRequestMappings().contains(closureMap.get(requestMappingInfo))) {
				closureMap.remove(requestMappingInfo);
			}
		}
		
//		for (Entry<RequestMappingInfo, ClosureRequestMapping> entry : new HashMap<RequestMappingInfo, ClosureRequestMapping>(closureMap).entrySet()) {
//			if (!show.getRest().getRequestMappings().contains(entry.getValue())) {
//				closureMap.remove(entry.getKey());
//			}
//		}
	}
	
	private void unregisterShowClosure(Show<?, ?> show) {
		showRequestMappingInfosMap.remove(show);
		for (ClosureRequestMapping requestMapping : show.getRest().getRequestMappings()) {
			RequestMappingInfo requestMappingInfo = requestMappingMvcRequestMappingAdapter.getRequestMappingInfo(requestMapping);
			if (LOGGER.isDebugEnabled()) LOGGER.debug("Unregistering "+requestMapping.getPaths());
			closureMap.remove(requestMappingInfo);
			Set<String> patterns = getMappingPathPatterns(requestMappingInfo);
			for (String pattern : patterns) {
				if (!getPathMatcher().isPattern(pattern)) {
					this.urlMap.remove(pattern);
				}
			}
		}
	}
	
	private class Match {

		private final RequestMappingInfo mapping;

		private final ClosureRequestMapping closureRequestMapping;

		private Match(RequestMappingInfo mapping, ClosureRequestMapping closureRequestMapping) {
			this.mapping = mapping;
			this.closureRequestMapping = closureRequestMapping;
		}

		@Override
		public String toString() {
			return this.mapping.toString();
		}
	}

	private class MatchComparator implements Comparator<Match> {

		private final Comparator<RequestMappingInfo> comparator;

		public MatchComparator(Comparator<RequestMappingInfo> comparator) {
			this.comparator = comparator;
		}

		@Override
		public int compare(Match match1, Match match2) {
			return this.comparator.compare(match1.mapping, match2.mapping);
		}
	}

	@Override
	public void onApplicationEvent(RestRegistrationEvent event) {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("Received rest registration event "+event.getShow() + " "+event.getAction());
		}
		switch (event.getAction()) {
		case CREATE:
			registerShowClosure(event.getShow());
			break;
			
		case REMOVE:
			unregisterShowClosure(event.getShow());
			break;
			
		case UPDATE:
			unregisterDeletedShowClosure(event.getShow());
			registerShowClosure(event.getShow(),true);
			break;
		default:
			break;
		}
	}
}
