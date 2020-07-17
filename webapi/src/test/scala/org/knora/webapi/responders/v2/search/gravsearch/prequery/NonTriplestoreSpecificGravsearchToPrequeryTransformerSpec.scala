package org.knora.webapi.responders.v2.search.gravsearch.prequery

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.ResponderData
import org.knora.webapi.responders.v2.search._
import org.knora.webapi.responders.v2.search.gravsearch.types.{GravsearchTypeInspectionRunner, GravsearchTypeInspectionUtil}
import org.knora.webapi.responders.v2.search.gravsearch.{GravsearchParser, GravsearchQueryChecker}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{AssertionException, CoreSpec, KnoraSettingsImpl, SharedTestDataADM}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._


private object QueryHandler {

    private val timeout = 10.seconds

    val anythingUser: UserADM = SharedTestDataADM.anythingAdminUser

    def transformQuery(query: String, responderData: ResponderData, settings: KnoraSettingsImpl): SelectQuery = {

        val constructQuery = GravsearchParser.parseQuery(query)

        val typeInspectionRunner = new GravsearchTypeInspectionRunner(responderData = responderData, inferTypes = true)

        val typeInspectionResultFuture = typeInspectionRunner.inspectTypes(constructQuery.whereClause, anythingUser)

        val typeInspectionResult = Await.result(typeInspectionResultFuture, timeout)

        val whereClauseWithoutAnnotations: WhereClause = GravsearchTypeInspectionUtil.removeTypeAnnotations(constructQuery.whereClause)

        // Validate schemas and predicates in the CONSTRUCT clause.
        GravsearchQueryChecker.checkConstructClause(
            constructClause = constructQuery.constructClause,
            typeInspectionResult = typeInspectionResult
        )

        // Create a Select prequery

        val nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificGravsearchToPrequeryTransformer = new NonTriplestoreSpecificGravsearchToPrequeryTransformer(
            constructClause = constructQuery.constructClause,
            typeInspectionResult = typeInspectionResult,
            querySchema = constructQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema")),
            settings = settings
        )

        val nonTriplestoreSpecificPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
            inputQuery = constructQuery.copy(whereClause = whereClauseWithoutAnnotations),
            transformer = nonTriplestoreSpecificConstructToSelectTransformer
        )

        nonTriplestoreSpecificPrequery
    }

}

class NonTriplestoreSpecificGravsearchToPrequeryTransformerSpec extends CoreSpec() {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val inputQueryWithDateNonOptionalSortCriterion: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing onto:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a onto:Thing .
          |
          |  ?thing onto:hasDate ?date .
          |  onto:hasDate knora-api:objectType knora-api:Date .
          |  ?date a knora-api:Date .
          |
          |}
          |ORDER BY DESC(?date)
        """.stripMargin

    val inputQueryWithDateNonOptionalSortCriterionComplex: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing onto:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a onto:Thing .
          |
          |  ?thing onto:hasDate ?date .
          |
          |}
          |ORDER BY DESC(?date)
        """.stripMargin

    val transformedQueryWithDateNonOptionalSortCriterion: SelectQuery =
        SelectQuery(
            variables = Vector(
                QueryVariable(variableName = "thing"),
                GroupConcat(
                    inputVariable = QueryVariable(variableName = "date"),
                    separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
                    outputVariableName = "date__Concat"
                )
            ),
            offset = 0,
            groupBy = Vector(
                QueryVariable(variableName = "thing"),
                QueryVariable(variableName = "date__valueHasStartJDN")
            ),
            orderBy = Vector(
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
                    isAscending = false
                ),
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "thing"),
                    isAscending = true
                )
            ),
            whereClause = WhereClause(
                patterns = ArrayBuffer(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                            propertyPathOperator = None
                        ),
                        namedGraph = None
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "date"),
                        namedGraph = None
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "date"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "date"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    )
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val inputQueryWithDateNonOptionalSortCriterionAndFilter: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing onto:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a onto:Thing .
          |
          |  ?thing onto:hasDate ?date .
          |  onto:hasDate knora-api:objectType knora-api:Date .
          |  ?date a knora-api:Date .
          |
          |  FILTER(?date > "GREGORIAN:2012-01-01"^^knora-api:Date)
          |
          |}
          |ORDER BY DESC(?date)
        """.stripMargin

    val inputQueryWithDateNonOptionalSortCriterionAndFilterComplex: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing onto:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a onto:Thing .
          |
          |  ?thing onto:hasDate ?date .
          |
          |  FILTER(knora-api:toSimpleDate(?date) > "GREGORIAN:2012-01-01"^^knora-api-simple:Date)
          |
          |}
          |ORDER BY DESC(?date)
        """.stripMargin

    val transformedQueryWithDateNonOptionalSortCriterionAndFilter: SelectQuery =
        SelectQuery(
            variables = Vector(
                QueryVariable(variableName = "thing"),
                GroupConcat(
                    inputVariable = QueryVariable(variableName = "date"),
                    separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
                    outputVariableName = "date__Concat"
                )
            ),
            offset = 0,
            groupBy = Vector(
                QueryVariable(variableName = "thing"),
                QueryVariable(variableName = "date__valueHasStartJDN")
            ),
            orderBy = Vector(
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
                    isAscending = false
                ),
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "thing"),
                    isAscending = true
                )
            ),
            whereClause = WhereClause(
                patterns = ArrayBuffer(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                            propertyPathOperator = None
                        ),
                        namedGraph = None
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "date"),
                        namedGraph = None
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "date"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "date"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    FilterPattern(expression = CompareExpression(
                        leftArg = QueryVariable(variableName = "date__valueHasStartJDN"),
                        operator = CompareExpressionOperator.GREATER_THAN,
                        rightArg = XsdLiteral(
                            value = "2455928",
                            datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                        )
                    ))
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val inputQueryWithDateOptionalSortCriterion: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing onto:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a onto:Thing .
          |
          |  OPTIONAL {
          |
          |    ?thing onto:hasDate ?date .
          |    onto:hasDate knora-api:objectType knora-api:Date .
          |    ?date a knora-api:Date .
          |
          |  }
          |
          |}
          |ORDER BY DESC(?date)
        """.stripMargin

    val inputQueryWithDateOptionalSortCriterionComplex: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing onto:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a onto:Thing .
          |
          |  OPTIONAL {
          |
          |    ?thing onto:hasDate ?date .
          |
          |  }
          |
          |}
          |ORDER BY DESC(?date)
        """.stripMargin

    val transformedQueryWithDateOptionalSortCriterion: SelectQuery =
        SelectQuery(
            variables = Vector(
                QueryVariable(variableName = "thing"),
                GroupConcat(
                    inputVariable = QueryVariable(variableName = "date"),
                    separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
                    outputVariableName = "date__Concat"
                )
            ),
            offset = 0,
            groupBy = Vector(
                QueryVariable(variableName = "thing"),
                QueryVariable(variableName = "date__valueHasStartJDN")
            ),
            orderBy = Vector(
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
                    isAscending = false
                ),
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "thing"),
                    isAscending = true
                )
            ),
            whereClause = WhereClause(
                patterns = ArrayBuffer(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                            propertyPathOperator = None
                        ),
                        namedGraph = None
                    ),
                    OptionalPattern(patterns = Vector(
                        StatementPattern(
                            subj = QueryVariable(variableName = "thing"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "date"),
                            namedGraph = None
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "date"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "false",
                                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                            ),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "date"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        )
                    ))
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val inputQueryWithDateOptionalSortCriterionAndFilter: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing onto:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a onto:Thing .
          |
          |  OPTIONAL {
          |
          |    ?thing onto:hasDate ?date .
          |    onto:hasDate knora-api:objectType knora-api:Date .
          |    ?date a knora-api:Date .
          |
          |    FILTER(?date > "GREGORIAN:2012-01-01"^^knora-api:Date)
          |  }
          |
          |}
          |ORDER BY DESC(?date)
        """.stripMargin

    val inputQueryWithDateOptionalSortCriterionAndFilterComplex: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing onto:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a onto:Thing .
          |
          |  OPTIONAL {
          |
          |    ?thing onto:hasDate ?date .
          |
          |    FILTER(knora-api:toSimpleDate(?date) > "GREGORIAN:2012-01-01"^^knora-api-simple:Date)
          |  }
          |
          |}
          |ORDER BY DESC(?date)
        """.stripMargin

    val transformedQueryWithDateOptionalSortCriterionAndFilter: SelectQuery =
        SelectQuery(
            variables = Vector(
                QueryVariable(variableName = "thing"),
                GroupConcat(
                    inputVariable = QueryVariable(variableName = "date"),
                    separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
                    outputVariableName = "date__Concat",
                )
            ),
            offset = 0,
            groupBy = Vector(
                QueryVariable(variableName = "thing"),
                QueryVariable(variableName = "date__valueHasStartJDN")
            ),
            orderBy = Vector(
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
                    isAscending = false
                ),
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "thing"),
                    isAscending = true
                )
            ),
            whereClause = WhereClause(
                patterns = ArrayBuffer(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                            propertyPathOperator = None
                        ),
                        namedGraph = None
                    ),
                    OptionalPattern(patterns = Vector(
                        StatementPattern(
                            subj = QueryVariable(variableName = "thing"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "date"),
                            namedGraph = None
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "date"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "false",
                                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                            ),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "date"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        FilterPattern(expression = CompareExpression(
                            leftArg = QueryVariable(variableName = "date__valueHasStartJDN"),
                            operator = CompareExpressionOperator.GREATER_THAN,
                            rightArg = XsdLiteral(
                                value = "2455928",
                                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                            )
                        ))
                    ))
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val inputQueryWithDecimalOptionalSortCriterion: String =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     OPTIONAL {
          |        ?thing anything:hasDecimal ?decimal .
          |        anything:hasDecimal knora-api:objectType xsd:decimal .
          |
          |        ?decimal a xsd:decimal .
          |     }
          |} ORDER BY ASC(?decimal)
        """.stripMargin

    val inputQueryWithDecimalOptionalSortCriterionComplex: String =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     OPTIONAL {
          |        ?thing anything:hasDecimal ?decimal .
          |     }
          |} ORDER BY ASC(?decimal)
        """.stripMargin

    val transformedQueryWithDecimalOptionalSortCriterion: SelectQuery =
        SelectQuery(
            variables = Vector(
                QueryVariable(variableName = "thing"),
                GroupConcat(
                    inputVariable = QueryVariable(variableName = "decimal"),
                    separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
                    outputVariableName = "decimal__Concat",
                )
            ),
            offset = 0,
            groupBy = Vector(
                QueryVariable(variableName = "thing"),
                QueryVariable(variableName = "decimal__valueHasDecimal")
            ),
            orderBy = Vector(
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
                    isAscending = true
                ),
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "thing"),
                    isAscending = true
                )
            ),
            whereClause = WhereClause(
                patterns = ArrayBuffer(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                            propertyPathOperator = None
                        ),
                        namedGraph = None
                    ),
                    OptionalPattern(patterns = Vector(
                        StatementPattern(
                            subj = QueryVariable(variableName = "thing"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "decimal"),
                            namedGraph = None
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "decimal"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "false",
                                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                            ),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "decimal"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "decimal__valueHasDecimal"),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        )
                    ))
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val inputQueryWithDecimalOptionalSortCriterionAndFilter: String =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     OPTIONAL {
          |        ?thing anything:hasDecimal ?decimal .
          |        anything:hasDecimal knora-api:objectType xsd:decimal .
          |
          |        ?decimal a xsd:decimal .
          |
          |        FILTER(?decimal > "2"^^xsd:decimal)
          |     }
          |} ORDER BY ASC(?decimal)
        """.stripMargin

    val inputQueryWithDecimalOptionalSortCriterionAndFilterComplex: String =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     OPTIONAL {
          |        ?thing anything:hasDecimal ?decimal .
          |
          |        ?decimal knora-api:decimalValueAsDecimal ?decimalVal .
          |
          |        FILTER(?decimalVal > "2"^^xsd:decimal)
          |     }
          |} ORDER BY ASC(?decimal)
        """.stripMargin

    val transformedQueryWithDecimalOptionalSortCriterionAndFilter: SelectQuery =
        SelectQuery(
            variables = Vector(
                QueryVariable(variableName = "thing"),
                GroupConcat(
                    inputVariable = QueryVariable(variableName = "decimal"),
                    separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
                    outputVariableName = "decimal__Concat"
                )
            ),
            offset = 0,
            groupBy = Vector(
                QueryVariable(variableName = "thing"),
                QueryVariable(variableName = "decimal__valueHasDecimal")
            ),
            orderBy = Vector(
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
                    isAscending = true
                ),
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "thing"),
                    isAscending = true
                )
            ),
            whereClause = WhereClause(
                patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                            propertyPathOperator = None
                        ),
                        namedGraph = None
                    ),
                    OptionalPattern(patterns = Vector(
                        StatementPattern(
                            subj = QueryVariable(variableName = "thing"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "decimal"),
                            namedGraph = None
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "decimal"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "false",
                                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                            ),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "decimal"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "decimal__valueHasDecimal"),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        FilterPattern(expression = CompareExpression(
                            leftArg = QueryVariable(variableName = "decimal__valueHasDecimal"),
                            operator = CompareExpressionOperator.GREATER_THAN,
                            rightArg = XsdLiteral(
                                value = "2",
                                datatype = "http://www.w3.org/2001/XMLSchema#decimal".toSmartIri
                            )
                        ))
                    ))
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex: SelectQuery =
        SelectQuery(
            variables = Vector(
                QueryVariable(variableName = "thing"),
                GroupConcat(
                    inputVariable = QueryVariable(variableName = "decimal"),
                    separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
                    outputVariableName = "decimal__Concat",
                )
            ),
            offset = 0,
            groupBy = Vector(
                QueryVariable(variableName = "thing"),
                QueryVariable(variableName = "decimal__valueHasDecimal")
            ),
            orderBy = Vector(
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
                    isAscending = true
                ),
                OrderCriterion(
                    queryVariable = QueryVariable(variableName = "thing"),
                    isAscending = true
                )
            ),
            whereClause = WhereClause(
                patterns = ArrayBuffer(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = XsdLiteral(
                            value = "false",
                            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                        ),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                            propertyPathOperator = None
                        ),
                        namedGraph = None
                    ),
                    OptionalPattern(patterns = Vector(
                        StatementPattern(
                            subj = QueryVariable(variableName = "thing"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "decimal"),
                            namedGraph = None
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "decimal"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "false",
                                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                            ),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "decimal"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "decimal__valueHasDecimal"),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "decimal"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "decimalVal"),
                            namedGraph = None
                        ),
                        FilterPattern(expression = CompareExpression(
                            leftArg = QueryVariable(variableName = "decimalVal"),
                            operator = CompareExpressionOperator.GREATER_THAN,
                            rightArg = XsdLiteral(
                                value = "2",
                                datatype = "http://www.w3.org/2001/XMLSchema#decimal".toSmartIri
                            )
                        ))
                    ))
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val InputQueryWithRdfsLabelAndLiteralInSimpleSchema: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
          |}
        """.stripMargin

    val InputQueryWithRdfsLabelAndLiteralInComplexSchema: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
          |}
        """.stripMargin

    val TransformedQueryWithRdfsLabelAndLiteral: SelectQuery = SelectQuery(
        variables = Vector(QueryVariable(variableName = "book")),
        offset = 0,
        groupBy = Vector(QueryVariable(variableName = "book")),
        orderBy = Vector(OrderCriterion(
            queryVariable = QueryVariable(variableName = "book"),
            isAscending = true
        )),
        whereClause = WhereClause(
            patterns = ArrayBuffer(
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = XsdLiteral(
                        value = "false",
                        datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                    ),
                    namedGraph = Some(IriRef(
                        iri = "http://www.knora.org/explicit".toSmartIri,
                        propertyPathOperator = None
                    ))
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = XsdLiteral(
                        value = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                        datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                    ),
                    namedGraph = None
                )
            ),
            positiveEntities = Set(),
            querySchema = None
        ),
        limit = Some(25),
        useDistinct = true
    )

    val InputQueryWithRdfsLabelAndVariableInSimpleSchema: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?label .
          |    FILTER(?label = "Zeitglöcklein des Lebens und Leidens Christi")
          |}
        """.stripMargin

    val InputQueryWithRdfsLabelAndVariableInComplexSchema: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?label .
          |    FILTER(?label = "Zeitglöcklein des Lebens und Leidens Christi")
          |}
        """.stripMargin


    val InputQueryWithRdfsLabelAndRegexInSimpleSchema: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    FILTER regex(?bookLabel, "Zeit", "i")
          |}""".stripMargin

    val InputQueryWithRdfsLabelAndRegexInComplexSchema: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    FILTER regex(?bookLabel, "Zeit", "i")
          |}""".stripMargin

    val TransformedQueryWithRdfsLabelAndVariable: SelectQuery = SelectQuery(
        variables = Vector(QueryVariable(variableName = "book")),
        offset = 0,
        groupBy = Vector(QueryVariable(variableName = "book")),
        orderBy = Vector(OrderCriterion(
            queryVariable = QueryVariable(variableName = "book"),
            isAscending = true
        )),
        whereClause = WhereClause(
            patterns = ArrayBuffer(
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = XsdLiteral(
                        value = "false",
                        datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                    ),
                    namedGraph = Some(IriRef(
                        iri = "http://www.knora.org/explicit".toSmartIri,
                        propertyPathOperator = None
                    ))
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "label"),
                    namedGraph = None
                ),
                FilterPattern(expression = CompareExpression(
                    leftArg = QueryVariable(variableName = "label"),
                    operator = CompareExpressionOperator.EQUALS,
                    rightArg = XsdLiteral(
                        value = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
                        datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                    )
                ))
            ),
            positiveEntities = Set(),
            querySchema = None
        ),
        limit = Some(25),
        useDistinct = true
    )

    val TransformedQueryWithRdfsLabelAndRegex: SelectQuery = SelectQuery(
        variables = Vector(QueryVariable(variableName = "book")),
        offset = 0,
        groupBy = Vector(QueryVariable(variableName = "book")),
        orderBy = Vector(OrderCriterion(
            queryVariable = QueryVariable(variableName = "book"),
            isAscending = true
        )),
        whereClause = WhereClause(
            patterns = ArrayBuffer(
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = XsdLiteral(
                        value = "false",
                        datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                    ),
                    namedGraph = Some(IriRef(
                        iri = "http://www.knora.org/explicit".toSmartIri,
                        propertyPathOperator = None
                    ))
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "bookLabel"),
                    namedGraph = None
                ),
                FilterPattern(expression = RegexFunction(
                    textExpr = QueryVariable(variableName = "bookLabel"),
                    pattern = "Zeit",
                    modifier = Some("i")
                ))
            ),
            positiveEntities = Set(),
            querySchema = None
        ),
        limit = Some(25),
        useDistinct = true
    )

    "The NonTriplestoreSpecificGravsearchToPrequeryGenerator object" should {

        "transform an input query with a date as a non optional sort criterion" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDateNonOptionalSortCriterion, responderData, settings)

            assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterion)

        }

        "transform an input query with a date as a non optional sort criterion (submitted in complex schema)" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDateNonOptionalSortCriterionComplex, responderData, settings)

            assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterion)

        }

        "transform an input query with a date as non optional sort criterion and a filter" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDateNonOptionalSortCriterionAndFilter, responderData, settings)

            assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterionAndFilter)

        }

        "transform an input query with a date as non optional sort criterion and a filter (submitted in complex schema)" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDateNonOptionalSortCriterionAndFilterComplex, responderData, settings)

            assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterionAndFilter)

        }

        "transform an input query with a date as an optional sort criterion" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDateOptionalSortCriterion, responderData, settings)

            assert(transformedQuery === transformedQueryWithDateOptionalSortCriterion)

        }

        "transform an input query with a date as an optional sort criterion (submitted in complex schema)" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDateOptionalSortCriterionComplex, responderData, settings)

            assert(transformedQuery === transformedQueryWithDateOptionalSortCriterion)

        }

        "transform an input query with a date as an optional sort criterion and a filter" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDateOptionalSortCriterionAndFilter, responderData, settings)

            assert(transformedQuery === transformedQueryWithDateOptionalSortCriterionAndFilter)

        }

        "transform an input query with a date as an optional sort criterion and a filter (submitted in complex schema)" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDateOptionalSortCriterionAndFilterComplex, responderData, settings)

            assert(transformedQuery === transformedQueryWithDateOptionalSortCriterionAndFilter)

        }


        "transform an input query with a decimal as an optional sort criterion" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDecimalOptionalSortCriterion, responderData, settings)

            assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterion)
        }

        "transform an input query with a decimal as an optional sort criterion (submitted in complex schema)" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDecimalOptionalSortCriterionComplex, responderData, settings)

            assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterion)
        }

        "transform an input query with a decimal as an optional sort criterion and a filter" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilter, responderData, settings)

            assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilter)
        }

        "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilterComplex, responderData, settings)

            // TODO: user provided statements and statement generated for sorting should be unified (https://github.com/dhlab-basel/Knora/issues/1195)
            assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex)
        }

        "transform an input query using rdfs:label and a literal in the simple schema" in {
            val transformedQuery = QueryHandler.transformQuery(InputQueryWithRdfsLabelAndLiteralInSimpleSchema, responderData, settings)

            assert(transformedQuery === TransformedQueryWithRdfsLabelAndLiteral)
        }

        "transform an input query using rdfs:label and a literal in the complex schema" in {
            val transformedQuery = QueryHandler.transformQuery(InputQueryWithRdfsLabelAndLiteralInComplexSchema, responderData, settings)

            assert(transformedQuery === TransformedQueryWithRdfsLabelAndLiteral)
        }

        "transform an input query using rdfs:label and a variable in the simple schema" in {
            val transformedQuery = QueryHandler.transformQuery(InputQueryWithRdfsLabelAndVariableInSimpleSchema, responderData, settings)

            assert(transformedQuery === TransformedQueryWithRdfsLabelAndVariable)
        }

        "transform an input query using rdfs:label and a variable in the complex schema" in {
            val transformedQuery = QueryHandler.transformQuery(InputQueryWithRdfsLabelAndVariableInComplexSchema, responderData, settings)

            assert(transformedQuery === TransformedQueryWithRdfsLabelAndVariable)
        }


        "transform an input query using rdfs:label and a regex in the simple schema" in {
            val transformedQuery = QueryHandler.transformQuery(InputQueryWithRdfsLabelAndRegexInSimpleSchema, responderData, settings)

            assert(transformedQuery === TransformedQueryWithRdfsLabelAndRegex)
        }

        "transform an input query using rdfs:label and a regex in the complex schema" in {
            val transformedQuery = QueryHandler.transformQuery(InputQueryWithRdfsLabelAndRegexInComplexSchema, responderData, settings)

            assert(transformedQuery === TransformedQueryWithRdfsLabelAndRegex)
        }
    }
}