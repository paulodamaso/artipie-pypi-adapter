/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 Artipie
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.pypi.http;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.memory.InMemoryStorage;
import com.artipie.http.hm.IsHeader;
import com.artipie.http.hm.ResponseMatcher;
import com.artipie.http.hm.RsHasBody;
import com.artipie.http.rq.RequestLine;
import com.artipie.http.rs.RsStatus;
import io.reactivex.Flowable;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link SliceIndex}.
 * @since 0.4
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class SliceIndexTest {

    @Test
    void returnsIndexListForRoot() {
        final Storage storage = new InMemoryStorage();
        final String path = "abc";
        storage.save(new Key.From(path, "abc-0.1.tar.gz"), new Content.From(new byte[]{})).join();
        storage.save(new Key.From(path, "abc-0.1.whl"), new Content.From(new byte[]{})).join();
        MatcherAssert.assertThat(
            new SliceIndex(storage).response(
                new RequestLine("GET", "/").toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new RsHasBody(SliceIndexTest.html(path))
        );
    }

    @Test
    void returnsIndexList() {
        final Storage storage = new InMemoryStorage();
        final String gzip = "def-0.1.tar.gz";
        final String wheel = "def-0.2.whl";
        storage.save(new Key.From("def", gzip), new Content.From(new byte[]{})).join();
        storage.save(new Key.From("def", wheel), new Content.From(new byte[]{})).join();
        storage.save(new Key.From("ghi", "jkl", "hij-0.3.whl"), new Content.From(new byte[]{}))
            .join();
        MatcherAssert.assertThat(
            new SliceIndex(storage).response(
                new RequestLine("GET", "/def").toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new RsHasBody(
                Matchers.anyOf(
                    new IsEqual<>(SliceIndexTest.html(gzip, wheel)),
                    new IsEqual<>(SliceIndexTest.html(wheel, gzip))
                )
            )
        );
    }

    @Test
    void returnsIndexListForMixedItems() {
        final Storage storage = new InMemoryStorage();
        final String rqline = "abc";
        final String file = "file.txt";
        final String one = "folder_one";
        final String two = "folder_two";
        storage.save(new Key.From(rqline, file), new Content.From(new byte[]{})).join();
        storage.save(new Key.From(rqline, two, file), new Content.From(new byte[]{})).join();
        storage.save(new Key.From(rqline, one, rqline, file), new Content.From(new byte[]{}))
            .join();
        storage.save(new Key.From("def", "ghi", "hij-0.3.whl"), new Content.From(new byte[]{}))
            .join();
        MatcherAssert.assertThat(
            new SliceIndex(storage).response(
                new RequestLine("GET", String.format("/%s", rqline)).toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new RsHasBody(
                Matchers.anyOf(
                    new IsEqual<>(SliceIndexTest.html(file, one, two)),
                    new IsEqual<>(SliceIndexTest.html(file, two, one)),
                    new IsEqual<>(SliceIndexTest.html(two, one, file)),
                    new IsEqual<>(SliceIndexTest.html(two, file, one)),
                    new IsEqual<>(SliceIndexTest.html(one, two, file)),
                    new IsEqual<>(SliceIndexTest.html(one, file, two))
                )
            )
        );
    }

    @Test
    void returnsIndexListForEmptyStorage() {
        final Storage storage = new InMemoryStorage();
        MatcherAssert.assertThat(
            new SliceIndex(storage).response(
                new RequestLine("GET", "/def").toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new RsHasBody("<!DOCTYPE html>\n<html>\n  </body>\n\n</body>\n</html>".getBytes())
        );
    }

    @Test
    void returnsStatusAndHeaders() {
        final Storage storage = new InMemoryStorage();
        final String path = "some";
        storage.save(new Key.From(path, "abc-0.0.1.tar.gz"), new Content.From(new byte[]{})).join();
        MatcherAssert.assertThat(
            new SliceIndex(storage).response(
                new RequestLine("GET", "/").toString(),
                Collections.emptyList(),
                Flowable.empty()
            ),
            new ResponseMatcher(
                RsStatus.OK,
                new IsHeader("Content-Type", "text/html"),
                new IsHeader("Content-Length", "78")
            )
        );
    }

    private static byte[] html(final String... items) {
        return
            String.format(
                "<!DOCTYPE html>\n<html>\n  </body>\n%s\n</body>\n</html>",
                Stream.of(items).map(
                    item -> String.format(
                        "<a href=\"/%s\">%s</a><br/>", item, item
                    )
                ).collect(Collectors.joining())
            ).getBytes();
    }

}
