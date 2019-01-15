package com.mozilla.secops.httprequest;

import static org.junit.Assert.assertEquals;

import com.mozilla.secops.DetectNat;
import com.mozilla.secops.TestUtil;
import com.mozilla.secops.alert.Alert;
import com.mozilla.secops.parser.Event;
import java.util.Map;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.windowing.IntervalWindow;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;
import org.joda.time.Instant;
import org.junit.Rule;
import org.junit.Test;

public class TestEndpointAbuse1 {
  public TestEndpointAbuse1() {}

  @Rule public final transient TestPipeline p = TestPipeline.create();

  private HTTPRequest.HTTPRequestOptions getTestOptions() {
    HTTPRequest.HTTPRequestOptions ret =
        PipelineOptionsFactory.as(HTTPRequest.HTTPRequestOptions.class);
    return ret;
  }

  @Test
  public void endpointAbuseTest() throws Exception {
    PCollection<String> input = TestUtil.getTestInput("/testdata/httpreq_endpointabuse1.txt", p);

    HTTPRequest.HTTPRequestOptions options = getTestOptions();
    String v[] = new String[1];
    v[0] = "8:GET:/test";
    options.setEndpointAbusePath(v);

    PCollection<Alert> results =
        input
            .apply(new HTTPRequest.ParseAndWindow(true))
            .apply(ParDo.of(new HTTPRequest.Preprocessor()))
            .apply(new HTTPRequest.EndpointAbuseAnalysis(options));

    PCollection<Long> count =
        results.apply(Combine.globally(Count.<Alert>combineFn()).withoutDefaults());

    PAssert.that(count)
        .inWindow(new IntervalWindow(new Instant(0L), new Instant(60000)))
        .containsInAnyOrder(1L);

    PAssert.that(results)
        .inWindow(new IntervalWindow(new Instant(0L), new Instant(60000)))
        .satisfies(
            i -> {
              for (Alert a : i) {
                assertEquals("192.168.1.2", a.getMetadataValue("sourceaddress"));
                assertEquals("endpoint_abuse", a.getMetadataValue("category"));
                assertEquals(10L, Long.parseLong(a.getMetadataValue("count"), 10));
                assertEquals("1970-01-01T00:00:59.999Z", a.getMetadataValue("window_timestamp"));
              }
              return null;
            });

    p.run().waitUntilFinish();
  }

  @Test
  public void endpointAbuseTestNatDetect() throws Exception {
    PCollection<String> input = TestUtil.getTestInput("/testdata/httpreq_endpointabuse1.txt", p);

    HTTPRequest.HTTPRequestOptions options = getTestOptions();
    String v[] = new String[1];
    v[0] = "8:GET:/test";
    options.setEndpointAbusePath(v);
    options.setNatDetection(true);

    PCollection<Event> events =
        input
            .apply(new HTTPRequest.ParseAndWindow(true))
            .apply(ParDo.of(new HTTPRequest.Preprocessor()));
    PCollectionView<Map<String, Boolean>> natView = DetectNat.getView(events);

    PCollection<Alert> results =
        events.apply(new HTTPRequest.EndpointAbuseAnalysis(options, natView));

    PCollection<Long> count =
        results.apply(Combine.globally(Count.<Alert>combineFn()).withoutDefaults());

    PAssert.that(count).inWindow(new IntervalWindow(new Instant(0L), new Instant(60000))).empty();

    p.run().waitUntilFinish();
  }
}
