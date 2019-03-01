package org.gbif.pipelines.transforms.core;

import java.util.List;

import org.gbif.pipelines.common.PipelinesVariables.Pipeline;
import org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.RecordType;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.transforms.CheckTransforms;

import org.apache.avro.file.CodecFactory;
import org.apache.beam.sdk.io.AvroIO;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.RecordType.VERBATIM;
import static org.gbif.pipelines.transforms.CheckTransforms.checkRecordType;

/**
 * Beam level transformations for the raw representation of DWC, read an avro, write an avro, from value to keyValue
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class VerbatimTransform {

  private static final CodecFactory BASE_CODEC = CodecFactory.snappyCodec();

  /**
   * Checks if list contains {@link RecordType#VERBATIM}, else returns empty {@link PCollection<ExtendedRecord>}
   */
  public static CheckTransforms<ExtendedRecord> check(List<String> types) {
    return CheckTransforms.create(ExtendedRecord.class, checkRecordType(types, VERBATIM));
  }

  /** Maps {@link ExtendedRecord} to key value, where key is {@link ExtendedRecord#getId} */
  public static MapElements<ExtendedRecord, KV<String, ExtendedRecord>> toKv() {
    return MapElements.into(new TypeDescriptor<KV<String, ExtendedRecord>>() {})
        .via((ExtendedRecord ex) -> KV.of(ex.getId(), ex));
  }

  /**
   * Reads avro files from path, which contains {@link ExtendedRecord}
   *
   * @param path path to source files
   */
  public static AvroIO.Read<ExtendedRecord> read(String path) {
    return AvroIO.read(ExtendedRecord.class).from(path);
  }

  /**
   * Writes {@link ExtendedRecord} *.avro files to path, data will be split into several files, uses
   * Snappy compression codec by default
   *
   * @param toPath path with name to output files, like - directory/name
   */
  public static AvroIO.Write<ExtendedRecord> write(String toPath) {
    return AvroIO.write(ExtendedRecord.class).to(toPath).withSuffix(Pipeline.AVRO_EXTENSION).withCodec(BASE_CODEC);
  }

}
