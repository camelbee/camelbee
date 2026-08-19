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

package org.camelbee.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.camel.util.URISupport;
import org.camelbee.masking.Masker;

/**
 * Redacts credentials out of endpoint URIs before they are published in the topology or in a traced
 * {@code Message}.
 *
 * <p><strong>Why this exists at all.</strong> A route written the careful way keeps its password in
 * a property - {@code .to("http://api?authPassword={{backend.password}}")}. The topology resolves
 * {@code {{...}}} placeholders so the UI can show what an endpoint actually talks to, which means it
 * also resolves that password. Without this class the secret is published by
 * {@code GET /camelbee/routes}, an endpoint that works even when tracing has never been enabled and
 * that no other redaction touches - {@code Masker} guards bodies and headers, which is a different
 * code path entirely.
 *
 * <p><strong>Why not just call {@link URISupport#sanitizeUri(String)}.</strong> Camel's sanitizer is
 * the right source of truth for <em>which</em> parameters are secret - it carries a curated list of
 * ~95 keywords and understands {@code RAW(...)} tokens. But its value pattern is {@code [^&]*}: a
 * value ends at an {@code &} or at the end of the string, nothing else. CamelBee does not hold bare
 * URIs. It holds the model's {@code toString()} - {@code To[...]}, {@code DynamicTo[toD[...]]},
 * {@code RecipientList[simple{a,b}]} - so a secret that is not followed by {@code &} swallows every
 * remaining character:
 *
 * <pre>
 * RecipientList[simple{http://a?password=x,http://b}]
 * -&gt; RecipientList[simple{http://a?password=xxxxxx // second recipient destroyed
 * </pre>
 *
 * <p>That is content loss, not cosmetics, and the UI's {@code endpointParser} relies on the trailing
 * {@code ]} to find endpoints at all.
 *
 * <p><strong>What this does instead.</strong> It splits the string on the delimiters Camel's pattern
 * does not recognise ({@code [ ] { } ,} and whitespace), sanitizes each bounded run on its own, then
 * reassembles with the delimiters intact. Inside a run there is no delimiter left to swallow, so
 * {@code [^&]*} is naturally bounded and Camel's keyword list and RAW handling do the actual work.
 * Configured {@code camelbee.masked-keys} are then applied on top, so one property governs bodies,
 * headers and URIs alike.
 *
 * <p><strong>Both sides must agree.</strong> The UI matches a traced message's endpoint against the
 * topology's URIs, and some of those comparisons are exact. Sanitizing one side only would silently
 * break edge matching, so this is applied to the topology (via
 * {@code RouteContextService.updateWithSystemProperties}) and to every {@code Message} (in its
 * canonical constructor) using this same method.
 */
public final class UriSanitizer {

  /**
   * Characters that bound a URI inside the model's {@code toString()} output but that Camel's
   * sanitizer treats as ordinary value characters. Splitting on these is what keeps a secret's
   * replacement from consuming the rest of the string.
   */
  private static final Pattern DELIMITERS = Pattern.compile("[\\[\\]{},\\s]");

  private UriSanitizer() {
  }

  /**
   * Redacts credentials in a URI, or in any string that contains one.
   *
   * @param value  the raw URI or model description, possibly null.
   * @param masker the configured masker, applied after Camel's own keyword list; may be null, in
   *               which case only Camel's list is applied.
   * @return the value with credential parameters and URI user-info passwords replaced.
   */
  public static String sanitize(String value, Masker masker) {
    if (value == null || value.isEmpty()) {
      return value;
    }

    /*
     Reassembled rather than replaced in place: every delimiter is preserved exactly, so a caller
     that parses the wrapper - the UI's endpointParser looks for a trailing ']' - sees the same
     shape it did before. Only the characters between delimiters can change.
     */
    final StringBuilder out = new StringBuilder(value.length() + 16);
    final Matcher delimiter = DELIMITERS.matcher(value);
    int cursor = 0;

    while (delimiter.find()) {
      out.append(sanitizeRun(value.substring(cursor, delimiter.start()), masker));
      out.append(delimiter.group());
      cursor = delimiter.end();
    }
    out.append(sanitizeRun(value.substring(cursor), masker));

    return out.toString();
  }

  private static String sanitizeRun(String run, Masker masker) {
    if (run.isEmpty()) {
      return run;
    }

    /*
     Camel's list first, and it is the whole story for a default configuration: it is curated for
     URI parameters, covers ~95 keywords including RAW tokens, and handles user-info credentials.
     */
    String sanitized = URISupport.sanitizeUri(run);

    /*
     CamelBee's own keys are layered on only when the application configured them. The DEFAULTS are
     deliberately NOT applied here: they are tuned for headers, where "auth" must catch
     "Authorization", and applying that breadth to a URI redacts ordinary configuration such as
     authMethod=Basic - hiding how an endpoint is set up while protecting nothing Camel's list has
     not already protected. An explicitly configured list is different: it was asked for.
     */
    if (masker != null && masker.hasCustomKeys()) {
      sanitized = masker.maskUri(sanitized);
    }

    return sanitized;
  }
}
