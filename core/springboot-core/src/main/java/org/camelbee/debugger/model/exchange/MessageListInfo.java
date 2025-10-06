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

package org.camelbee.debugger.model.exchange;

import java.time.Instant;

/**
 * Contains metadata about the message list for efficient change detection.
 */
public class MessageListInfo {

  private final int count;
  private final long resetVersion;
  private final long addVersion;
  private final Instant lastModified;
  private final Instant lastResetTime;

  public MessageListInfo(int count, long resetVersion, long addVersion, Instant lastModified, Instant lastResetTime) {
    this.count = count;
    this.resetVersion = resetVersion;
    this.addVersion = addVersion;
    this.lastModified = lastModified;
    this.lastResetTime = lastResetTime;
  }

  public int getCount() {
    return count;
  }

  public long getResetVersion() {
    return resetVersion;
  }

  public long getAddVersion() {
    return addVersion;
  }

  public Instant getLastModified() {
    return lastModified;
  }

  public Instant getLastResetTime() {
    return lastResetTime;
  }
}