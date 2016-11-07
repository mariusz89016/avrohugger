/** MACHINE-GENERATED FROM AVRO SCHEMA. DO NOT EDIT DIRECTLY */
package default.values.test

sealed trait DefaultValues extends Product with Serializable

final case class MetaEvent(atype: String = "aType", service: String = "df", service_timezone: Option[String] = None, btype: String = "cvb") extends DefaultValues

final case class Visit(first_meta_event: MetaEvent = new MetaEvent("tyu", "df", None, "cvb"), second_meta_event: MetaEvent = new MetaEvent("aType", "df", None, "cvb"), last_meta_event: MetaEvent = new MetaEvent("asd", "df", None, "345f")) extends DefaultValues