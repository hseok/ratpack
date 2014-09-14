/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.http;

import io.netty.handler.codec.http.HttpHeaders;
import ratpack.api.Nullable;
import ratpack.util.MultiValueMap;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * An immutable set of HTTP headers.
 */
public interface Headers {

  /**
   * Returns the header value with the specified header name.
   * <p>
   * If there is more than one header value for the specified header name, the first value is returned.
   *
   * @param name The case insensitive name of the header to get retrieve the first value of
   * @return the header value or {@code null} if there is no such header
   */
  @Nullable
  String get(CharSequence name);
  /**
   * Returns the header value with the specified header name.
   * <p>
   * If there is more than one header value for the specified header name, the first value is returned.
   *
   * @param name The case insensitive name of the header to get retrieve the first value of
   * @return the header value or {@code null} if there is no such header
   */
  @Nullable
  String get(String name);

  /**
   * Returns the header value as a date with the specified header name.
   * <p>
   * If there is more than one header value for the specified header name, the first value is returned.
   *
   * @param name The case insensitive name of the header to get retrieve the first value of
   * @return the header value as a date or {@code null} if there is no such header or the header value is not a valid date format
   */
  @Nullable
  Date getDate(CharSequence name);
  /**
   * Returns the header value as a date with the specified header name.
   * <p>
   * If there is more than one header value for the specified header name, the first value is returned.
   *
   * @param name The case insensitive name of the header to get retrieve the first value of
   * @return the header value as a date or {@code null} if there is no such header or the header value is not a valid date format
   */
  @Nullable
  Date getDate(String name);

  /**
   * Returns all of the header values with the specified header name.
   *
   * @param name The case insensitive name of the header to retrieve all of the values of
   * @return the {@link java.util.List} of header values, or an empty list if there is no such header
   */
  List<String> getAll(CharSequence name);

  /**
   * Returns all of the header values with the specified header name.
   *
   * @param name The case insensitive name of the header to retrieve all of the values of
   * @return the {@link java.util.List} of header values, or an empty list if there is no such header
   */
  List<String> getAll(String name);

  /**
   * Checks whether a header has been specified for the given value.
   *
   * @param name The name of the header to check the existence of
   * @return True if there is a header with the specified header name
   */
  boolean contains(CharSequence name);

  /**
   * Checks whether a header has been specified for the given value.
   *
   * @param name The name of the header to check the existence of
   * @return True if there is a header with the specified header name
   */
  boolean contains(String name);

  /**
   * All header names.
   *
   * @return The names of all headers that were sent
   */
  Set<String> getNames();

  /**
   * Returns the headers in their Netty compliant form.
   * <p>
   * Use of this method should be avoided, in favor of using the other methods of this interface.
   *
   * @return the headers in their Netty compliant form
   */
  HttpHeaders getNettyHeaders();

  MultiValueMap<String, String> asMultiValueMap();

}
