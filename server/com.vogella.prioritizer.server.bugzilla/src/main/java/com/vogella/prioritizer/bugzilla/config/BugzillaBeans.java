	package com.vogella.prioritizer.bugzilla.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jakewharton.retrofit2.adapter.reactor.ReactorCallAdapterFactory;
import com.vogella.prioritizer.bugzilla.BugzillaApi;

import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
public class BugzillaBeans {

	private Retrofit retrofit;

	@Bean
	public BugzillaApi getBugzillaApi() {
		if (null == retrofit) {
			retrofit = new Retrofit.Builder().baseUrl(BugzillaApi.BASE_URL)
					.addCallAdapterFactory(ReactorCallAdapterFactory.create())
					.addConverterFactory(JacksonConverterFactory.create()).build();
		}

		return retrofit.create(BugzillaApi.class);
	}
}
