/*
 * Copyright 2023 Rahmi Ege Karaosmanoglu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camelbee.springboot.example.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the MongoClient bean referenced by the mongodb routes ("mongodb:mongoBean").
 * Defined explicitly because Spring Boot 4 moved the MongoDB client auto-configuration into a
 * separate module, so the driver alone (via camel-mongodb-starter) no longer yields a bean.
 */
@Configuration
public class MongoConfig {

  /**
   * MongoClient bean named mongoBean.
   */
  @Bean
  public MongoClient mongoBean(@Value("${spring.data.mongodb.uri}") String uri) {
    return MongoClients.create(uri);
  }
}
