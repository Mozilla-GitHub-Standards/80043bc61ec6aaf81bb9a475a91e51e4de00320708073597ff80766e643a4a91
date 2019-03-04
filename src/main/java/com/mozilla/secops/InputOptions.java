package com.mozilla.secops;

import org.apache.beam.sdk.extensions.gcp.options.GcpOptions;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubOptions;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;

/** Standard input options for pipelines. */
public interface InputOptions extends PipelineOptions, PubsubOptions, GcpOptions {
  @Description("Read from Pubsub (multiple allowed); Pubsub topic")
  String[] getInputPubsub();

  void setInputPubsub(String[] value);

  @Description("Read from file (multiple allowed); File path")
  String[] getInputFile();

  void setInputFile(String[] value);

  @Description("Path to load Maxmind database; resource path, gcs path")
  String getMaxmindDbPath();

  void setMaxmindDbPath(String value);

  @Description("Enable XFF address selector; comma delimited list of trusted CIDR format subnets")
  String getXffAddressSelector();

  void setXffAddressSelector(String value);
}
