package avrohugger
package matchers

import treehugger.forest._
import definitions._
import treehuggerDSL._

import org.apache.avro.Schema

import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.map.ObjectMapper
import org.codehaus.jackson.node.{ NullNode, ObjectNode, TextNode }

import scala.collection.JavaConversions._

object DefaultValueMatcher {
  
  // This code was stolen from here:
  // https://github.com/julianpeeters/avro-scala-macro-annotations/blob/104fa325a00044ff6d31184fa7ff7b6852e9acd5/macros/src/main/scala/avro/scala/macro/annotations/provider/matchers/FromJsonMatcher.scala
  def getDefaultValue(field: Schema.Field, typeMatcher: TypeMatcher): Tree = {

    val nullNode = new TextNode("null")

    def fromJsonNode(node: JsonNode, schema: Schema): Tree = {
      schema.getType match {
        case _ if node == null => EmptyTree //not `default=null`, but no default
        case Schema.Type.INT => LIT(node.getIntValue)
        case Schema.Type.FLOAT => LIT(node.getDoubleValue.asInstanceOf[Float])
        case Schema.Type.LONG => LIT(node.getLongValue)
        case Schema.Type.DOUBLE => LIT(node.getDoubleValue)
        case Schema.Type.BOOLEAN => LIT(node.getBooleanValue)
        case Schema.Type.STRING => LIT(node.getTextValue)

        case Schema.Type.ENUM => (REF(schema.getName) DOT node.getTextValue)
        case Schema.Type.NULL => LIT(null)
        case Schema.Type.UNION => {
          val unionSchemas = schema.getTypes.toList
          if (unionSchemas.length == 2 &&
            unionSchemas.exists(schema => schema.getType == Schema.Type.NULL) &&
            unionSchemas.exists(schema => schema.getType != Schema.Type.NULL)) {
            val maybeSchema = unionSchemas.find(schema => schema.getType != Schema.Type.NULL)
            maybeSchema match {
              case Some(unionSchema) => {
                node match {
                  case `nullNode` => NONE
                  case nn: NullNode => NONE
                  case nonNullNode => SOME(fromJsonNode(nonNullNode, unionSchema))
                }
              }
              case None => throw new Exception("no avro type found in this union")
            }
          }
          else throw new Exception("Unsupported union field")
        }
        case Schema.Type.ARRAY => {
          val maybeCustom = typeMatcher.customTypeMap.get("array")
          val collectionType = {
            DefaultParamMatcher.checkCustomArrayType(maybeCustom, ListClass)
          }
          collectionType APPLY(node.getElements.toSeq.map(e => fromJsonNode(e, schema.getElementType)))
        }
        case Schema.Type.MAP => {
          val kvps = node.getFields.toList.map(e => LIT(e.getKey) ANY_-> fromJsonNode(e.getValue, schema.getValueType))
          MAKE_MAP(kvps)
        }
        case Schema.Type.RECORD  => {
          val fields  = schema.getFields
          val jsObject = node match {
            case t: TextNode =>
              val mapper = new ObjectMapper();
              mapper.readValue(t.getTextValue, classOf[ObjectNode])
            case o: ObjectNode => o
            case _ => throw new Exception(s"Invalid default value for field: $field, value: $node")
          }

          val defaultValues = fields.map(x => (x.name, fromJsonNode(x.defaultValue, x.schema)))
          val fieldValues = fields.map { f => (f.name, fromJsonNode(jsObject.get(f.name), f.schema))}.filter(!_._2.isEmpty)

          val values = (defaultValues.toMap ++ fieldValues.toMap)
            .map(e => {
              if (e._2.isEmpty) {
                throw new Exception("Some values for non-default fields not provided")
              } else e
            })

          val output = defaultValues.map(x => values(x._1))
          NEW(schema.getName, output: _*)
        }
        case x => throw new Exception("Can't extract a default field, type not yet supported: " + x)
      }
    }

    fromJsonNode(field.defaultValue, field.schema)
  }
}