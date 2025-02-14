package org.gbif.pipelines.core.interpreters.metadata;

import com.google.common.base.Strings;
import java.net.URI;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.gbif.api.model.registry.MachineTag;
import org.gbif.api.util.VocabularyUtils;
import org.gbif.api.vocabulary.EndpointType;
import org.gbif.api.vocabulary.License;
import org.gbif.api.vocabulary.TagName;
import org.gbif.common.parsers.LicenseParser;
import org.gbif.pipelines.core.ws.metadata.MetadataServiceClient;
import org.gbif.pipelines.core.ws.metadata.response.Dataset;
import org.gbif.pipelines.core.ws.metadata.response.Installation;
import org.gbif.pipelines.core.ws.metadata.response.Network;
import org.gbif.pipelines.core.ws.metadata.response.Organization;
import org.gbif.pipelines.io.avro.MetadataRecord;

/** Interprets GBIF metadata by datasetId */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MetadataInterpreter {

  /** Gets information from GBIF API by datasetId */
  public static BiConsumer<String, MetadataRecord> interpret(MetadataServiceClient client) {
    return (datasetId, mdr) -> {

      // Set required metadata properties
      mdr.setDatasetKey(datasetId);
      mdr.setNetworkKeys(Collections.emptyList());

      if (client != null) {

        Dataset dataset = client.getDataset(datasetId);

        // https://github.com/gbif/pipelines/issues/401
        License license = getLicense(dataset.getLicense());
        if (license == null || license == License.UNSPECIFIED || license == License.UNSUPPORTED) {
          throw new IllegalArgumentException(
              "Dataset licence can't be UNSPECIFIED or UNSUPPORTED!");
        } else {
          mdr.setLicense(license.name());
        }

        mdr.setDatasetTitle(dataset.getTitle());
        mdr.setInstallationKey(dataset.getInstallationKey());
        mdr.setPublishingOrganizationKey(dataset.getPublishingOrganizationKey());

        List<Network> networkList = client.getNetworkFromDataset(datasetId);
        if (networkList != null && !networkList.isEmpty()) {
          mdr.setNetworkKeys(
              networkList.stream().map(Network::getKey).collect(Collectors.toList()));
        }

        Organization organization = client.getOrganization(dataset.getPublishingOrganizationKey());
        mdr.setEndorsingNodeKey(organization.getEndorsingNodeKey());
        mdr.setPublisherTitle(organization.getTitle());
        mdr.setDatasetPublishingCountry(organization.getCountry());
        getLastCrawledDate(dataset.getMachineTags())
            .ifPresent(d -> mdr.setLastCrawled(d.getTime()));
        if (Objects.nonNull(dataset.getProject())) {
          mdr.setProjectId(dataset.getProject().getIdentifier());
          if (Objects.nonNull(dataset.getProject().getProgramme())) {
            mdr.setProgrammeAcronym(dataset.getProject().getProgramme().getAcronym());
          }
        }

        Installation installation = client.getInstallation(dataset.getInstallationKey());
        mdr.setHostingOrganizationKey(installation.getOrganizationKey());

        copyMachineTags(dataset.getMachineTags(), mdr);
      }
    };
  }

  /** Sets attempt number as crawlId */
  public static Consumer<MetadataRecord> interpretCrawlId(Integer attempt) {
    return mdr -> Optional.ofNullable(attempt).ifPresent(mdr::setCrawlId);
  }

  /**
   * Gets information about dataset source endpoint type (DWC_ARCHIVE, BIOCASE_XML_ARCHIVE, TAPIR ..
   * etc)
   */
  public static Consumer<MetadataRecord> interpretEndpointType(String endpointType) {
    return mdr -> {
      if (!Strings.isNullOrEmpty(endpointType)) {
        VocabularyUtils.lookup(endpointType, EndpointType.class)
            .ifPresent(x -> mdr.setProtocol(x.name()));
      }
    };
  }

  /** Returns ENUM instead of url string */
  private static License getLicense(String url) {
    URI uri =
        Optional.ofNullable(url)
            .map(
                x -> {
                  try {
                    return URI.create(x);
                  } catch (IllegalArgumentException ex) {
                    return null;
                  }
                })
            .orElse(null);
    License license = LicenseParser.getInstance().parseUriThenTitle(uri, null);
    // UNSPECIFIED must be mapped to null
    return License.UNSPECIFIED == license ? null : license;
  }

  /** Gets the latest crawl attempt time, if exists. */
  private static Optional<Date> getLastCrawledDate(List<MachineTag> machineTags) {
    return Optional.ofNullable(machineTags)
        .flatMap(
            x ->
                x.stream()
                    .filter(
                        tag ->
                            TagName.CRAWL_ATTEMPT.getName().equals(tag.getName())
                                && TagName.CRAWL_ATTEMPT
                                    .getNamespace()
                                    .getNamespace()
                                    .equals(tag.getNamespace()))
                    .sorted(Comparator.comparing(MachineTag::getCreated).reversed())
                    .map(MachineTag::getCreated)
                    .findFirst());
  }

  /** Copy MachineTags into the Avro Metadata record. */
  private static void copyMachineTags(List<MachineTag> machineTags, MetadataRecord mdr) {
    if (Objects.nonNull(machineTags) && !machineTags.isEmpty()) {
      mdr.setMachineTags(
          machineTags.stream()
              .map(
                  machineTag ->
                      org.gbif.pipelines.io.avro.MachineTag.newBuilder()
                          .setNamespace(machineTag.getNamespace())
                          .setName(machineTag.getName())
                          .setValue(machineTag.getValue())
                          .build())
              .collect(Collectors.toList()));
    }
  }
}
