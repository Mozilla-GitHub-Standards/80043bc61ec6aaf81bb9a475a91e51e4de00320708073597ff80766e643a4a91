package com.mozilla.secops.workshop;

import com.mozilla.secops.CompositeInput;
import com.mozilla.secops.InputOptions;
import java.io.IOException;
import java.io.Serializable;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PDone;

/**
 * Getting started with Beam workshop pipeline.
 *
 * <p>This class is not meant for production use but instead is part of the introduction to Beam
 * workshop that is part of the foxsec-pipeline repository.
 */
public class Workshop implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * An output transform that simply prints a string
   *
   * <p>Does not need to be modified for workshop.
   */
  public static class PrintOutput extends PTransform<PCollection<String>, PDone> {
    private static final long serialVersionUID = 1L;

    @Override
    public PDone expand(PCollection<String> input) {
      input.apply(
          ParDo.of(
              new DoFn<String, Void>() {
                private static final long serialVersionUID = 1L;

                @ProcessElement
                public void processElement(ProcessContext c) {
                  System.out.println(c.element());
                }
              }));
      return PDone.in(input.getPipeline());
    }
  }

  /** DoFn to perform extraction of words from each line of input. */
  public static class ExtractWords extends DoFn<String, String> {
    private static final long serialVersionUID = 1L;

    @ProcessElement
    public void processElement(ProcessContext c) {
      // Needs to be adjusted, initially here we just output the same input value
      //
      // Hint: for extraction, you can call output() multiple times.
      String input = c.element();
      c.output(input);
    }
  }

  /** Runtime options for {@link Workshop} pipeline. */
  public interface WorkshopOptions extends InputOptions, PipelineOptions {}

  private static void runWorkshop(WorkshopOptions options) throws IOException {
    Pipeline p = Pipeline.create(options);

    // Read the input text into a PCollection of type String
    PCollection<String> input = p.apply("input", new CompositeInput(options));

    // Next, parallelize extraction of individual words from each line in the input text using the
    // ExtractWords DoFn. ExtractWords is already templated out in this file, but the processElement
    // method will need to be modified.
    PCollection<String> words = input.apply(ParDo.of(new ExtractWords()));

    // Apply a count transform to extract the number of occurances of each word
    //
    // Hint: you will want to use the Count transform with perElement().
    // https://beam.apache.org/releases/javadoc/2.8.0/org/apache/beam/sdk/transforms/Count.html
    //
    // ADD CODE
    // e.g., PCollection<KV<String, Long>> wordcounts = words.apply(...)

    // The count transform should produce an output that consists of a series of KV elements,
    // where the key is a string (the word) and the value is the count. Next, parallelize
    // conversion of these KV elements into Strings so they can be printed using PrintOutput.
    //
    // There are a couple ways to go about this, the simplest is to create another DoFn similar
    // to ExtractWords that takes KV<String, Long> as input and produces a String output.
    // e.g., PCollection<String> myoutput = wordcounts.apply(ParDo.of(new FormatCount()));
    //
    // The second method is to use the MapElements transform with an associated Lambda for example.
    //
    // ADD CODE

    // Optional: Using the KV PCollection generated by the Count transform earlier, calculate the
    // average number of occurences for words in the dataset.
    //
    // Hint: you will want to use a combination of the Values transform, the Mean transform, and
    // another DoFn to convert the Double output from Mean into a String for output.
    //
    // You may also want to use the Flatten tranform to combine the output of the mean calculation
    // with the output of the counts calculation, so they are contained in the same PCollection
    // before calling PrintOutput.
    //
    // ADD CODE

    // Print the final output; this transform expects it's input to be a PCollection of type
    // String. This should print the final output in format "<count> <word>", one entry per
    // line. Optionally, if mean has also been calculated this should add one additional line
    // of output of format "mean value: <calculated value>".
    //
    // The initial example code just prints the input, so this will need to be changed to apply
    // to the correct PCollection.
    input.apply(new PrintOutput());

    p.run().waitUntilFinish();
  }

  /**
   * Entry point for Beam pipeline.
   *
   * @param args Runtime arguments.
   */
  public static void main(String[] args) throws IOException {
    PipelineOptionsFactory.register(WorkshopOptions.class);
    WorkshopOptions options =
        PipelineOptionsFactory.fromArgs(args).withValidation().as(WorkshopOptions.class);
    runWorkshop(options);
  }
}
