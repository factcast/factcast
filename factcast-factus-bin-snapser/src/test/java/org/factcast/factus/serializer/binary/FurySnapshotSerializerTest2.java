/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.factus.serializer.binary;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.factcast.factus.projection.SnapshotProjection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FurySnapshotSerializerTest2 {

  static Root root;

  static {
    ObjectMapper om =
        new ObjectMapper().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION.mappedFeature());
    try {
      root = om.readValue(TestData.HUGE_JSON, Root.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private final FurySnapshotSerializer underTest = new FurySnapshotSerializer();

  @Nested
  class Bench {

    @RepeatedTest(10000)
    void canDeserialize(TestInfo i) {
      root.kind = i.getDisplayName();
      root.data.after = i.getDisplayName();
      TestProjection source = new TestProjection().root(root);
      TestProjection target =
          underTest.deserialize(TestProjection.class, underTest.serialize(source));
      assertEquals("bar", target.foo());
      assertEquals(source.hashCode(), target.hashCode());
    }

    @Test
    void size() {
      TestProjection source = new TestProjection().root(root);
      System.out.println(underTest.serialize(source).length);
    }
  }

  @Test
  void hasId() {
    org.assertj.core.api.Assertions.assertThat(underTest.id().name())
        .isEqualTo("FurySnapshotSerializer");
  }

  @Nested
  class WhenSerializing {

    @RepeatedTest(10000)
    void canDeserialize(TestInfo i) {

      root.kind = i.getDisplayName();
      System.out.println(i.getDisplayName());
      TestProjection source = new TestProjection().root(root);
      TestProjection source2 = new TestProjection().root(root);
      assertEquals(source.hashCode(), source2.hashCode());

      TestProjection target =
          underTest.deserialize(TestProjection.class, underTest.serialize(source));
      assertEquals("bar", target.foo());

      assertEquals(source.hashCode(), target.hashCode());
    }

    @Test
    void compresses() {
      CompressableTestProjection testProjection = new CompressableTestProjection();
      byte[] bytes = underTest.serialize(testProjection);
      CompressableTestProjection b = underTest.deserialize(CompressableTestProjection.class, bytes);
      assertEquals(b.someString(), testProjection.someString());
      // lots of same chars in there, should be able to compress to 50%
      // including the overhead of msgpack
      assertTrue(bytes.length < (testProjection.someString().length() / 2));
    }
  }

  @Nested
  class whenDeserializing {
    @Test
    void upcastingWorks() {
      // INIT
      TestClassV1 testClassV1 = new TestClassV1();
      testClassV1.id = "123";
      testClassV1.x = 5;
      testClassV1.y = 9;

      // RUN
      byte[] serializedV1 = underTest.serialize(testClassV1);

      // ASSERT
      TestClassV1a_noRelevantChange deserializedV1a =
          underTest.deserialize(TestClassV1a_noRelevantChange.class, serializedV1);

      assertThat(deserializedV1a.id).isEqualTo(testClassV1.id);

      assertThat(deserializedV1a.x).isEqualTo(testClassV1.x);

      assertThat(deserializedV1a.y).isEqualTo(testClassV1.y);
    }

    @Test
    void downcastingWorks() {
      // INIT
      TestClassV1a_noRelevantChange testClassV1a = new TestClassV1a_noRelevantChange();
      testClassV1a.id = "123";
      testClassV1a.x = 5;
      testClassV1a.y = 9;
      testClassV1a.ignoreMe = "xxx";

      // RUN
      byte[] serializedV1 = underTest.serialize(testClassV1a);

      // ASSERT
      TestClassV1 deserializedV1 = underTest.deserialize(TestClassV1.class, serializedV1);

      assertThat(deserializedV1.id).isEqualTo(testClassV1a.id);

      assertThat(deserializedV1.x).isEqualTo(testClassV1a.x);

      assertThat(deserializedV1.y).isEqualTo(testClassV1a.y);
    }
  }

  static class TestClassV1 implements SnapshotProjection {
    int x;

    int y;

    String id;
  }

  /** Order of items changed, ignored item added */
  static class TestClassV1a_noRelevantChange implements SnapshotProjection {
    int y;

    int x;

    String id;

    @JsonIgnore String ignoreMe;
  }

  static class TestClassV2a_withChanges_newField implements SnapshotProjection {
    int x;

    int y;

    String id;

    int i;
  }

  static class TestClassV2b_withChanges_typeChanged implements SnapshotProjection {
    int x;

    int y;

    UUID id;
  }

  static class TestClassV2c_withChanges_fieldRenamed implements SnapshotProjection {
    int x;

    int y;

    String userId;
  }
}

@lombok.Data
@JsonAutoDetect
class _222thktisryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class _2e4ty3gisryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class _2wwnswgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class _71rj760jsryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class _7tl9nxpisryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class _9plsixgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Abkgelwisryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Atez5xgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Bqb3uwgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Buvty5isxryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Child {
  public String kind;
  public Data data;
}

@lombok.Data
@JsonAutoDetect
class Data {

  public String after;

  public int dist;

  public String modhash;

  public Object geo_filter;

  public ArrayList<Child> children;

  public Object before;

  public Object approved_at_utc;

  public String subreddit;

  public String selftext;

  public String author_fullname;

  public boolean saved;

  public Object mod_reason_title;

  public int gilded;

  public boolean clicked;

  public String title;

  public ArrayList<LinkFlairRichtext> link_flair_richtext;

  public String subreddit_name_prefixed;

  public boolean hidden;

  public int pwls;

  public String link_flair_css_class;

  public int downs;

  public int thumbnail_height;

  public Object top_awarded_type;

  public boolean hide_score;

  public String name;

  public boolean quarantine;

  public String link_flair_text_color;

  public double upvote_ratio;

  public String author_flair_background_color;

  public String subreddit_type;

  public int ups;

  public int total_awards_received;

  public MediaEmbed media_embed;

  public int thumbnail_width;

  public Object author_flair_template_id;

  public boolean is_original_content;

  public ArrayList<Object> user_reports;

  public SecureMedia secure_media;

  public boolean is_reddit_media_domain;

  public boolean is_meta;

  public Object category;

  public SecureMediaEmbed secure_media_embed;

  public String link_flair_text;

  public boolean can_mod_post;

  public int score;

  public Object approved_by;

  public boolean is_created_from_ads_ui;

  public boolean author_premium;

  public String thumbnail;

  public boolean edited;

  public String author_flair_css_class;

  public ArrayList<Object> author_flair_richtext;

  public Gildings gildings;

  public String post_hint;

  public ArrayList<String> content_categories;

  public boolean is_self;

  public Object mod_note;

  public double created;

  public String link_flair_type;

  public int wls;

  public Object removed_by_category;

  public Object banned_by;

  public String author_flair_type;

  public String domain;

  public boolean allow_live_comments;

  public String selftext_html;

  public Object likes;

  public String suggested_sort;

  public Object banned_at_utc;

  public String url_overridden_by_dest;

  public Object view_count;

  public boolean archived;

  public boolean no_follow;

  public boolean is_crosspostable;

  public boolean pinned;

  public boolean over_18;

  public Preview preview;

  public ArrayList<Object> all_awardings;

  public ArrayList<Object> awarders;

  public boolean media_only;

  public boolean can_gild;

  public boolean spoiler;

  public boolean locked;

  public String author_flair_text;

  public ArrayList<Object> treatment_tags;

  public boolean visited;

  public Object removed_by;

  public Object num_reports;

  public Object distinguished;

  public String subreddit_id;

  public boolean author_is_blocked;

  public Object mod_reason_by;

  public Object removal_reason;

  public String link_flair_background_color;

  public String id;

  public boolean is_robot_indexable;

  public Object report_reasons;

  public String author;

  public Object discussion_type;

  public int num_comments;

  public boolean send_replies;

  public boolean contest_mode;

  public ArrayList<Object> mod_reports;

  public boolean author_patreon_flair;

  public String author_flair_text_color;

  public String permalink;

  public boolean stickied;

  public String url;

  public int subreddit_subscribers;

  public double created_utc;

  public int num_crossposts;

  public Media media;

  public boolean is_video;

  public String link_flair_template_id;

  public boolean is_gallery;

  public MediaMetadata media_metadata;

  public GalleryData gallery_data;
}

@lombok.Data
@JsonAutoDetect
class Edgjrwgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Exk0w0hpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class GalleryData {

  public ArrayList<Item> items;
}

@lombok.Data
@JsonAutoDetect
class Gi2fnwgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Gildings {}

@lombok.Data
@JsonAutoDetect
class Hg9wlxgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Hnkl36isxryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Hrjj2xgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Image {

  public Source source;

  public ArrayList<Resolution> resolutions;

  public Variants variants;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Item {

  public String media_id;

  public int id;

  public String caption;
}

@lombok.Data
@JsonAutoDetect
class Jk3lpwgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Jre8pj2jsryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class K7olywgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Kgsgwwgpcsyd1 {

  public String status;
  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class L5kdbxgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class LinkFlairRichtext {

  public String e;

  public String t;

  public String a;

  public String u;
}

@lombok.Data
@JsonAutoDetect
class Media {

  public RedditVideo reddit_video;
}

@lombok.Data
@JsonAutoDetect
class MediaEmbed {}

@lombok.Data
@JsonAutoDetect
class MediaMetadata {

  public Buvty5isxryd1 buvty5isxryd1;

  public Hnkl36isxryd1 hnkl36isxryd1;

  public N7oth5isxryd1 n7oth5isxryd1;

  public Nsk69xgpcsyd1 nsk69xgpcsyd1;

  @JsonProperty("9plsixgpcsyd1")
  public _9plsixgpcsyd1 _9plsixgpcsyd1;

  public Gi2fnwgpcsyd1 gi2fnwgpcsyd1;

  public Hrjj2xgpcsyd1 hrjj2xgpcsyd1;

  public Hg9wlxgpcsyd1 hg9wlxgpcsyd1;

  public Kgsgwwgpcsyd1 kgsgwwgpcsyd1;

  public Edgjrwgpcsyd1 edgjrwgpcsyd1;
  public L5kdbxgpcsyd1 l5kdbxgpcsyd1;
  public Zddurxgpcsyd1 zddurxgpcsyd1;

  @JsonProperty("2wwnswgpcsyd1")
  public _2wwnswgpcsyd1 _2wwnswgpcsyd1;

  public Exk0w0hpcsyd1 exk0w0hpcsyd1;

  public Bqb3uwgpcsyd1 bqb3uwgpcsyd1;

  public Jk3lpwgpcsyd1 jk3lpwgpcsyd1;

  public K7olywgpcsyd1 k7olywgpcsyd1;

  public Atez5xgpcsyd1 atez5xgpcsyd1;

  public Rkbh8xgpcsyd1 rkbh8xgpcsyd1;

  public Abkgelwisryd1 abkgelwisryd1;

  @JsonProperty("7tl9nxpisryd1")
  public _7tl9nxpisryd1 _7tl9nxpisryd1;

  @JsonProperty("2e4ty3gisryd1")
  public _2e4ty3gisryd1 _2e4ty3gisryd1;

  public Jre8pj2jsryd1 jre8pj2jsryd1;

  @JsonProperty("222thktisryd1")
  public _222thktisryd1 _222thktisryd1;

  @JsonProperty("71rj760jsryd1")
  public _71rj760jsryd1 _71rj760jsryd1;

  public Ocwc28kisryd1 ocwc28kisryd1;
}

@lombok.Data
@JsonAutoDetect
class N7oth5isxryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Nsfw {

  public Source source;

  public ArrayList<Resolution> resolutions;
}

@lombok.Data
@JsonAutoDetect
class Nsk69xgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Obfuscated {

  public Source source;

  public ArrayList<Resolution> resolutions;
}

@lombok.Data
@JsonAutoDetect
class Ocwc28kisryd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class P {

  public int y;

  public int x;

  public String u;
}

@lombok.Data
@JsonAutoDetect
class Preview {

  public ArrayList<Image> images;

  public boolean enabled;
}

@lombok.Data
@JsonAutoDetect
class RedditVideo {

  public int bitrate_kbps;

  public String fallback_url;

  public boolean has_audio;

  public int height;

  public int width;

  public String scrubber_media_url;

  public String dash_url;

  public int duration;

  public String hls_url;

  public boolean is_gif;

  public String transcoding_status;
}

@lombok.Data
@JsonAutoDetect
class Resolution {

  public String url;

  public int width;

  public int height;
}

@lombok.Data
@JsonAutoDetect
class Rkbh8xgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}

@lombok.Data
@JsonAutoDetect
class Root {

  public String kind;

  public Data data;
}

@lombok.Data
@JsonAutoDetect
class S {

  public int y;

  public int x;

  public String u;
}

@lombok.Data
@JsonAutoDetect
class SecureMedia {

  public RedditVideo reddit_video;
}

@lombok.Data
@JsonAutoDetect
class SecureMediaEmbed {}

@lombok.Data
@JsonAutoDetect
class Source {

  public String url;

  public int width;

  public int height;
}

@lombok.Data
@JsonAutoDetect
class Variants {

  public Obfuscated obfuscated;

  public Nsfw nsfw;
}

@lombok.Data
@JsonAutoDetect
class Zddurxgpcsyd1 {

  public String status;

  public String e;

  public String m;

  public ArrayList<P> p;

  public S s;

  public String id;
}
