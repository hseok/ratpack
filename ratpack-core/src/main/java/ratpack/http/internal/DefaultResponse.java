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

package ratpack.http.internal;

import com.google.common.collect.Sets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.ServerCookieEncoder;
import org.reactivestreams.Publisher;
import ratpack.api.Nullable;
import ratpack.exec.ExecControl;
import ratpack.file.internal.FileHttpTransmitter;
import ratpack.func.Action;
import ratpack.http.*;
import ratpack.stream.internal.StreamTransmitter;
import ratpack.util.ExceptionUtils;
import ratpack.util.MultiValueMap;
import ratpack.util.internal.IoUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static ratpack.file.internal.DefaultFileRenderer.readAttributes;
import static ratpack.http.internal.HttpHeaderConstants.CONTENT_TYPE;

public class DefaultResponse implements Response {

  private final MutableStatus status;
  private final MutableHeaders headers;
  private final FileHttpTransmitter fileHttpTransmitter;
  private final StreamTransmitter streamTransmitter;
  private final Action<? super ByteBuf> committer;
  private final ByteBufAllocator byteBufAllocator;

  private boolean contentTypeSet;
  private Set<Cookie> cookies;

  public DefaultResponse(MutableStatus status, MutableHeaders headers, FileHttpTransmitter fileHttpTransmitter, StreamTransmitter streamTransmitter, ByteBufAllocator byteBufAllocator, Action<? super ByteBuf> committer) {
    this.status = status;
    this.fileHttpTransmitter = fileHttpTransmitter;
    this.streamTransmitter = streamTransmitter;
    this.byteBufAllocator = byteBufAllocator;
    this.headers = new MutableHeadersWrapper(headers);
    this.committer = committer;
  }

  class MutableHeadersWrapper implements MutableHeaders {

    private final MutableHeaders wrapped;

    MutableHeadersWrapper(MutableHeaders wrapped) {
      this.wrapped = wrapped;
    }

    @Override
    public MutableHeaders add(CharSequence name, Object value) {
      if (!contentTypeSet && (name == CONTENT_TYPE || name.toString().equalsIgnoreCase(CONTENT_TYPE.toString()))) {
        contentTypeSet = true;
      }

      wrapped.add(name, value);
      return this;
    }

    @Override
    public MutableHeaders set(CharSequence name, Object value) {
      if (!contentTypeSet && (name == CONTENT_TYPE || name.toString().equalsIgnoreCase(CONTENT_TYPE.toString()))) {
        contentTypeSet = true;
      }

      wrapped.set(name, value);
      return this;
    }

    @Override
    public MutableHeaders setDate(CharSequence name, Date value) {
      wrapped.set(name, value);
      return this;
    }

    @Override
    public MutableHeaders set(CharSequence name, Iterable<?> values) {
      if (!contentTypeSet && (name == CONTENT_TYPE || name.toString().equalsIgnoreCase(CONTENT_TYPE.toString()))) {
        contentTypeSet = true;
      }

      wrapped.set(name, values);
      return this;
    }

    @Override
    public MutableHeaders remove(CharSequence name) {
      if (name == CONTENT_TYPE || name.toString().equalsIgnoreCase(CONTENT_TYPE.toString())) {
        contentTypeSet = false;
      }

      wrapped.remove(name);
      return this;
    }

    @Override
    public MutableHeaders clear() {
      contentTypeSet = false;
      wrapped.clear();
      return this;
    }

    @Override
    public MutableHeaders copy(Headers headers) {
      this.wrapped.copy(headers);
      if (headers.contains(HttpHeaderConstants.CONTENT_TYPE)) {
        contentTypeSet = true;
      }
      return this;
    }

    @Override
    public MultiValueMap<String, String> asMultiValueMap() {
      return wrapped.asMultiValueMap();
    }

    @Nullable
    @Override
    public String get(CharSequence name) {
      return wrapped.get(name);
    }

    @Nullable
    @Override
    public String get(String name) {
      return wrapped.get(name);
    }

    @Nullable
    @Override
    public Date getDate(CharSequence name) {
      return wrapped.getDate(name);
    }

    @Override
    @Nullable
    public Date getDate(String name) {
      return wrapped.getDate(name);
    }

    @Override
    public List<String> getAll(CharSequence name) {
      return wrapped.getAll(name);
    }

    @Override
    public List<String> getAll(String name) {
      return wrapped.getAll(name);
    }

    @Override
    public boolean contains(CharSequence name) {
      return wrapped.contains(name);
    }

    @Override
    public boolean contains(String name) {
      return wrapped.contains(name);
    }

    @Override
    public Set<String> getNames() {
      return wrapped.getNames();
    }

    @Override
    public HttpHeaders getNettyHeaders() {
      return wrapped.getNettyHeaders();
    }
  }

  public MutableStatus getStatus() {
    return status;
  }

  public Response status(int code) {
    status.set(code);
    return this;
  }

  public Response status(int code, String message) {
    status.set(code, message);
    return this;
  }

  @Override
  public Response status(Status status) {
    return status(status.getCode(), status.getMessage());
  }

  @Override
  public MutableHeaders getHeaders() {
    return headers;
  }

  public void send() {
    commit(byteBufAllocator.buffer(0, 0));
  }

  @Override
  public Response contentType(CharSequence contentType) {
    headers.set(CONTENT_TYPE, contentType);
    return this;
  }

  @Override
  public Response contentTypeIfNotSet(CharSequence contentType) {
    if (!contentTypeSet) {
      contentType(contentType);
    }
    return this;
  }

  public void send(String text) {
    contentTypeIfNotSet(HttpHeaderConstants.PLAIN_TEXT_UTF8).send(IoUtils.utf8Bytes(text));
  }

  public void send(CharSequence contentType, String body) {
    contentType(contentType);
    send(body);
  }

  public void send(byte[] bytes) {
    contentTypeIfNotSet(HttpHeaderConstants.OCTET_STREAM);
    commit(Unpooled.wrappedBuffer(bytes));
  }

  public void send(CharSequence contentType, byte[] bytes) {
    contentType(contentType).send(bytes);
  }

  @Override
  public void send(InputStream inputStream) throws IOException {
    commit(IoUtils.writeTo(inputStream, byteBufAllocator.buffer()));
  }

  @Override
  public void send(CharSequence contentType, InputStream inputStream) throws IOException {
    contentType(contentType).send(inputStream);
  }

  public void send(CharSequence contentType, ByteBuf buffer) {
    contentType(contentType);
    send(buffer);
  }

  public void send(ByteBuf buffer) {
    contentTypeIfNotSet(HttpHeaderConstants.OCTET_STREAM);
    commit(buffer);
  }

  @Override
  public void sendFile(ExecControl execContext, BasicFileAttributes attributes, Path file) {
    setCookieHeader();
    fileHttpTransmitter.transmit(execContext, attributes, file);
  }

  @Override
  public void sendStream(ExecControl execControl, Publisher<? extends ByteBuf> stream) {
    setCookieHeader();
    streamTransmitter.transmit(execControl, stream);
  }

  public void sendFile(final ExecControl execContext, final Path file) {
    try {
      readAttributes(execContext, file, new Action<BasicFileAttributes>() {
        public void execute(BasicFileAttributes fileAttributes) throws Exception {
          sendFile(execContext, fileAttributes, file);
        }
      });
    } catch (Exception e) {
      // Shouldn't happen
      throw ExceptionUtils.uncheck(e);
    }
  }

  public Set<Cookie> getCookies() {
    if (cookies == null) {
      cookies = Sets.newHashSet();
    }
    return cookies;
  }

  public Cookie cookie(String name, String value) {
    Cookie cookie = new DefaultCookie(name, value);
    getCookies().add(cookie);
    return cookie;
  }

  public Cookie expireCookie(String name) {
    Cookie cookie = cookie(name, "");
    cookie.setMaxAge(0);
    return cookie;
  }

  private void setCookieHeader() {
    if (cookies != null && !cookies.isEmpty()) {
      for (Cookie cookie : cookies) {
        headers.add(HttpHeaderConstants.SET_COOKIE, ServerCookieEncoder.encode(cookie));
      }
    }
  }

  private void commit(ByteBuf byteBuf) {
    setCookieHeader();
    try {
      committer.execute(byteBuf);
    } catch (Exception e) {
      throw ExceptionUtils.uncheck(e);
    }
  }
}
