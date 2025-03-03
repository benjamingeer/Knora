workspace "Architecture Diagrams for DSP" "This is a collection of diagrams for the DSP architecture" {
    # https://github.com/structurizr/dsl/blob/master/docs/language-reference.md
    # for an example, see https://structurizr.com/dsl?example=big-bank-plc

    

    # static model
    model {
        !include users.dsl
        
        enterprise "DSP - DaSCH Service Platform" {
            # softwareSystem <name> [description] [tags]
            dspJsLib        = softwaresystem "JS-LIB"               "Layer between DSP-API and DSP-APP"
            dspApp          = softwaresystem "DSP-APP"              "app.dasch.swiss"
            dspTools        = softwaresystem "DSP-TOOLS"            "CLI for DSP-API"
            fuseki          = softwaresystem "Fuseki Triplestore"   "RDF database"                          "Database"
            sipi            = softwaresystem "SIPI"                 "IIIF image server"
            arkResolver     = softwaresystem "ARK Resolver"         "Forwards ARK URLs to DSP-APP URLs"
            dspApi          = softwareSystem "DSP-API"   "api.dasch.swiss" {
                # container <name> [description] [technology] [tags]
                
                eventStoreService    = container "Event Store Service"
                eventListener        = container "Event Listener"
                triplestoreService   = container "Triplestore Service"
                arkService           = container "ARK Service"
                elasticSearchService = container "Elastic Search Service"

                sharedProject           = container "Shared Project" "The project that handles shared entities" {
                    valueObjectsPackage     = component "ValueObjects Package" "The package that provides value objects"
                    errorPackage            = component "Error Package" "The package that provides errors"
                }

                webapiProject     = container "Webapi" "The project that wraps webapi V2"
                
                projectSlice      = container "Project Slice" "The slice that handles projects"
                roleSlice         = container "Role Slice" "The slice that handles roles"
                schemaSlice       = container "Schema Slice" "The slice that handles schemas"
                resourceSlice     = container "Resource Slice" "The slice that handles resources"
                listSlice         = container "List Slice" "The slice that handles lists"
                routes            = container "Routes" "The slice that provides all routes"
                
                userSlice         = container "User Slice" "The slice that handles users" {
                    userCore        = component "User Core"
                    userDomain      = component "User Domain"
                    userHandler     = component "User Handler"

                    userRepo        = component "User Repo (API)"
                    userRepoLive    = component "User Repo Live (Implementation)"
                    userRepoMock    = component "User Repo Mock (Implementation for Tests)"
                    
                    userRoute       = component "User Route"
                }
                
            }
        }

        # relationships between users and software systems
        user             -> dspApp       "Uses [Browser]"
        user             -> arkResolver  "Uses [Browser]"
        user             -> dspTools     "Uses [CLI]"

        # relationships to/from software systems
        dspApp      -> dspJsLib
        dspJsLib    -> dspApi
        dspTools    -> dspApi
        dspApi      -> fuseki 
        dspApi      -> sipi
        dspTools    -> sipi
        
        # relationships to/from containers
        webapiProject -> sharedProject "depends on"
        projectSlice  -> sharedProject "depends on"
        roleSlice     -> sharedProject "depends on"
        schemaSlice   -> sharedProject "depends on"
        resourceSlice -> sharedProject "depends on"
        listSlice     -> sharedProject "depends on"
        routes        -> sharedProject "depends on"
        userSlice     -> sharedProject "depends on"

        # relationships to/from components
        userRepo        -> userCore "depends on"
        userRepoLive    -> userCore "depends on"
        userRepoMock    -> userCore "depends on"
        userRoute       -> userCore "depends on"

        userRepoLive    -> userRepo "implements"
        userRepoMock    -> userRepo "implements"

        userCore        -> userDomain  "contains"
        userCore        -> userHandler "contains"

    }

    views {
        systemlandscape "SystemLandscape" "System Landscape of DSP-API" {
            include *
            autoLayout
        }

        # systemContext <software system identifier> [key] [description]
        systemContext dspApi "SystemContextDspApi" "DSP-API System Context" {
            include *
            autoLayout
        }

        # container <software system identifier> [key] [description]
        container dspApi "ContainerDspApi" "Containers of DSP-API" {
            include *
            autoLayout
        }

        # component <container identifier> [key] [description]
        component userSlice "ComponentsOfUserSlice" "Components of the User Slice" {
            include *

        }

        component SharedProject "ComponentsOfSharedProject" "Components of the Shared Project" {
            include *
            autoLayout
        }

        # dynamic <*|software system identifier|container identifier> [key] [description]
        dynamic userSlice "HttpRequestWithEventsCreateUser" "Example workflow for a HTTP request with events (create user)" {
            user -> userRoute "sends HTTP request to"
            userRoute -> userRoute "validates input and creates value objects"
            userRoute -> userHandler "createUser(vo)"
            userHandler -> userRepo "reserve username"
            userRepo -> eventStoreService "reserve username"
            eventStoreService -> eventStoreService "check if username exists"
            eventStoreService -> eventStoreService "reserve username"
            userHandler -> userDomain ".make(vo)"
            userDomain -> userDomain "create user domain entity + userCreatedEvent(who, what)"
            userDomain -> userHandler "return (userDomainEntity + userCreatedEvent)"
            userHandler -> userRepo "storeUser(userDomainEntity + userCreatedEvent)"
            userRepo -> eventStoreService "storeUser(userDomainEntity + userCreatedEvent)"
            eventStoreService -> eventStoreService "store event(s), userCreatedEvent(who, what, when(!))"
            eventStoreService -> eventListener "publishEvent(userCreatedEvent)"
            eventListener -> triplestoreService "writeToTsService(E)"
            triplestoreService -> triplestoreService "SPARQL Update"
            eventListener -> arkService "writeToArkService(E)"
            arkService -> arkService "create ARK(URL)"
            eventListener -> elasticSearchService "writeToEsService(E)"

        }

        dynamic userSlice "HttpRequestWithEventsUpdateUser" "Example workflow for a HTTP request with events (update username)" {
            user -> userRoute "sends HTTP request to"
            userRoute -> userRoute "validates input and creates value objects"
            userRoute -> userHandler "updateUsername(vo)"
            userHandler -> userRepo "getUser(userId)"
            userRepo -> eventStoreService "getUser(userId)"
            eventStoreService -> eventStoreService "get all events for this user"
            eventStoreService -> userDomain "createUserFromEvents(E,E,E,...)"
            userDomain -> userHandler "return User"
            userHandler -> userDomain "updateUsername(vo)"
            userDomain -> userDomain "updateUser(userDomainEntity + userUpdatedEvent(who, what))"
            userDomain -> userHandler "return userDomainEntity + userUpdatedEvent(who, what)"
            userHandler -> userRepo "storeUser(userDomainEntity + userUpdatedEvent(who, what, when(!))"

            userRepo -> eventStoreService "storeUser(userDomainEntity + userCreatedEvent)"
            eventStoreService -> eventStoreService "store event(s), userCreatedEvent(who, what, when(!))"
            eventStoreService -> eventListener "publishEvent(userCreatedEvent)"
            eventListener -> triplestoreService "writeToTsService(E)"
            triplestoreService -> triplestoreService "SPARQL Update"
            eventListener -> arkService "writeToArkService(E)"
            arkService -> arkService "create ARK(URL)"
            eventListener -> elasticSearchService "writeToEsService(E)"

            autoLayout
        }

        theme default
    }

    !adrs decisions
    !docs docs
}
