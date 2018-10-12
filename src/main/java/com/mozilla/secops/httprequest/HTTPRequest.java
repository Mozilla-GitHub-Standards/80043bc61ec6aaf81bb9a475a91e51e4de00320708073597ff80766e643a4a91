package com.mozilla.secops.httprequest;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.Mean;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mozilla.secops.InputOptions;
import com.mozilla.secops.OutputOptions;
import com.mozilla.secops.parser.Event;
import com.mozilla.secops.parser.Parser;
import com.mozilla.secops.parser.Payload;
import com.mozilla.secops.parser.GLB;

import org.joda.time.DateTime;
import org.joda.time.Duration;

import java.io.Serializable;
import java.util.UUID;

/**
 * {@link HTTPRequest} describes and implements a Beam pipeline for analysis of HTTP
 * requests using log data.
 */
public class HTTPRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Composite transform to parse a {@link PCollection} containing events as strings
     * and emit a {@link PCollection} of {@link Event} objects.
     *
     * <p>The output is windowed into fixed windows of one minute. This function discards
     * events that are not considered HTTP requests.
     */
    public static class ParseAndWindow extends PTransform<PCollection<String>,
           PCollection<Event>> {
        private static final long serialVersionUID = 1L;

        @Override
        public PCollection<Event> expand(PCollection<String> col) {
            class Parse extends DoFn<String, Event> {
                private static final long serialVersionUID = 1L;

                private Logger log;
                private Parser ep;
                private Boolean noteIgnoringTZ = false;
                private Long parseCount;

                @Setup
                public void Setup() {
                    ep = new Parser();
                    log = LoggerFactory.getLogger(Parse.class);
                    log.info("initialized new parser");
                }

                @StartBundle
                public void StartBundle() {
                    log.info("processing new bundle");
                    parseCount = 0L;
                }

                @FinishBundle
                public void FinishBundle() {
                    log.info("{} events processed in bundle", parseCount);
                }

                @ProcessElement
                public void processElement(ProcessContext c) {
                    Event e = ep.parse(c.element());
                    if (e != null && e.getPayloadType() == Payload.PayloadType.GLB) {
                        GLB g = e.getPayload();
                        if (!e.getTimestamp().getZone().getID().equals("Etc/UTC")) {
                            if (!noteIgnoringTZ) {
                                log.warn("ignoring events with non-UTC timestamp");
                                noteIgnoringTZ = true;
                            }
                        } else {
                            parseCount++;
                            c.outputWithTimestamp(e, e.getTimestamp().toInstant());
                        }
                    }
                }
            }

            return col.apply(ParDo.of(new Parse()))
                .apply(Window.<Event>into(FixedWindows.of(Duration.standardMinutes(1))));
        }
    }

    /**
     * Composite transform which given a set of windowed {@link Event} types, emits a
     * set of {@link KV} objects where the key is the source address of the request and
     * the value is the number of requests for that source within the window.
     */
    public static class CountInWindow extends PTransform<PCollection<Event>,
           PCollection<KV<String, Long>>> {
        private static final long serialVersionUID = 1L;

        @Override
        public PCollection<KV<String, Long>> expand(PCollection<Event> col) {
            class GetSourceAddress extends DoFn<Event, String> {
                private static final long serialVersionUID = 1L;

                @ProcessElement
                public void processElement(ProcessContext c) {
                    GLB g = c.element().getPayload();
                    c.output(g.getSourceAddress());
                }
            }

            return col.apply(ParDo.of(new GetSourceAddress()))
                .apply(Count.<String>perElement());
        }
    }

    /**
     * Composite transform that conducts threshold analysis using the configured threshold
     * modifier across a set of KV objects as returned by {@link CountInWindow}.
     */
    public static class ThresholdAnalysis extends PTransform<PCollection<KV<String, Long>>,
           PCollection<Result>> {
        private static final long serialVersionUID = 1L;

        private final Double thresholdModifier;

        /**
         * Static initializer for {@link ThresholdAnalysis}.
         *
         * @param thresholdModifier Threshold modifier to use for analysis.
         */
        public ThresholdAnalysis(Double thresholdModifier) {
            this.thresholdModifier = thresholdModifier;
        }

        @Override
        public PCollection<Result> expand(PCollection<KV<String, Long>> col) {
            PCollection<Long> counts = col.apply("Extract counts", ParDo.of(
                        new DoFn<KV<String, Long>, Long>() {
                            private static final long serialVersionUID = 1L;

                            @ProcessElement
                            public void processElement(ProcessContext c) {
                                c.output(c.element().getValue());
                            }
                        }
                        ));
            final PCollectionView<Double> meanValue =
                counts.apply(Mean.<Long>globally().asSingletonView());

            PCollection<Result> ret = col.apply(ParDo.of(
                        new DoFn<KV<String, Long>, Result>() {
                            private static final long serialVersionUID = 1L;

                            @ProcessElement
                            public void processElement(ProcessContext c, BoundedWindow w) {
                                Double mv = c.sideInput(meanValue);
                                if (c.element().getValue() >= (mv * thresholdModifier)) {
                                    Result r = Result.fromKV(c.element());
                                    r.setMeanValue(mv);
                                    r.setThresholdModifier(thresholdModifier);
                                    r.setWindowTimestamp(new DateTime(w.maxTimestamp()));
                                    c.output(r);
                                }
                            }
                        }
                        ).withSideInputs(meanValue));
            return ret;
        }
    }

    /**
     * {@link DoFn} to transform any generated {@link Result} objects into JSON for
     * consumption by output transforms.
     */
    public static class OutputFormat extends DoFn<Result, String> {
        private static final long serialVersionUID = 1L;

        @ProcessElement
        public void processElement(ProcessContext c) {
            c.output(c.element().toJSON());
        }
    }

    /**
     * Runtime options for {@link HTTPRequest} pipeline.
     */
    public interface HTTPRequestOptions extends PipelineOptions, InputOptions, OutputOptions {
        @Description("Analysis threshold modifier")
        @Default.Double(75.0)
        Double getAnalysisThresholdModifier();
        void setAnalysisThresholdModifier(Double value);
    }

    private static void runHTTPRequest(HTTPRequestOptions options) {
        Pipeline p = Pipeline.create(options);

        PCollection<String> results = p.apply("input", options.getInputType().read(options))
            .apply("parse and window", new ParseAndWindow())
            .apply("count in window", new CountInWindow())
            .apply("threshold analysis", new ThresholdAnalysis(options.getAnalysisThresholdModifier()))
            .apply("output format", ParDo.of(new OutputFormat()));

        results.apply("output", OutputOptions.compositeOutput(options));

        p.run();
    }

    /**
     * Entry point for Beam pipeline.
     *
     * @param args Runtime arguments.
     */
    public static void main(String[] args) {
        PipelineOptionsFactory.register(HTTPRequestOptions.class);
        HTTPRequestOptions options =
            PipelineOptionsFactory.fromArgs(args).withValidation().as(HTTPRequestOptions.class);
        runHTTPRequest(options);
    }
}