/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionGroupApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionHasPermissionsApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionPropertyApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionResourceClassApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsForProjectGetResponseADM
import org.knora.webapi.slice.admin.api.PermissionEndpointsRequests.ChangeDoapForWhatRequest
import org.knora.webapi.slice.admin.api.service.PermissionRestService
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class PermissionsEndpointsHandlers(
  permissionsEndpoints: PermissionsEndpoints,
  restService: PermissionRestService,
  mapper: HandlerMapper,
) {

  private val postPermissionsApHandler =
    SecuredEndpointHandler[
      CreateAdministrativePermissionAPIRequestADM,
      AdministrativePermissionCreateResponseADM,
    ](
      permissionsEndpoints.postPermissionsAp,
      user => { case (request: CreateAdministrativePermissionAPIRequestADM) =>
        restService.createAdministrativePermission(request, user)
      },
    )

  private val getPermissionsApByProjectIriHandler =
    SecuredEndpointHandler[ProjectIri, AdministrativePermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsApByProjectIri,
      user => { case (projectIri: ProjectIri) =>
        restService.getPermissionsApByProjectIri(projectIri, user)
      },
    )

  private val getPermissionsApByProjectAndGroupIriHandler =
    SecuredEndpointHandler[(ProjectIri, GroupIri), AdministrativePermissionGetResponseADM](
      permissionsEndpoints.getPermissionsApByProjectAndGroupIri,
      user => { case (projectIri: ProjectIri, groupIri: GroupIri) =>
        restService.getPermissionsApByProjectAndGroupIri(projectIri, groupIri, user)
      },
    )

  private val getPermissionsDaopByProjectIriHandler =
    SecuredEndpointHandler[ProjectIri, DefaultObjectAccessPermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsDoapByProjectIri,
      user => { case (projectIri: ProjectIri) =>
        restService.getPermissionsDaopByProjectIri(projectIri, user)
      },
    )

  private val getPermissionsByProjectIriHandler =
    SecuredEndpointHandler[ProjectIri, PermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsByProjectIri,
      user => { case (projectIri: ProjectIri) =>
        restService.getPermissionsByProjectIri(projectIri, user)
      },
    )

  private val deletePermissionHandler =
    SecuredEndpointHandler[PermissionIri, PermissionDeleteResponseADM](
      permissionsEndpoints.deletePermission,
      user => { case (permissionIri: PermissionIri) =>
        restService.deletePermission(permissionIri, user)
      },
    )

  private val postPermissionsDoapHandler =
    SecuredEndpointHandler[
      CreateDefaultObjectAccessPermissionAPIRequestADM,
      DefaultObjectAccessPermissionCreateResponseADM,
    ](
      permissionsEndpoints.postPermissionsDoap,
      user => { case (request: CreateDefaultObjectAccessPermissionAPIRequestADM) =>
        restService.createDefaultObjectAccessPermission(request, user)
      },
    )

  private val putPermissionsDoapForWhatHandler =
    SecuredEndpointHandler[
      (PermissionIri, ChangeDoapForWhatRequest),
      DefaultObjectAccessPermissionGetResponseADM,
    ](
      permissionsEndpoints.putPermissionsDoapForWhat,
      user => { case (permissionIri: PermissionIri, request: ChangeDoapForWhatRequest) =>
        restService.updateDoapForWhat(permissionIri, request, user)
      },
    )

  private val putPermissionsProjectIriGroupHandler =
    SecuredEndpointHandler[
      (PermissionIri, ChangePermissionGroupApiRequestADM),
      PermissionGetResponseADM,
    ](
      permissionsEndpoints.putPermissionsProjectIriGroup,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionGroupApiRequestADM) =>
        restService.updatePermissionGroup(permissionIri, request, user)
      },
    )

  private val putPermissionsHasPermissionsHandler =
    SecuredEndpointHandler[
      (PermissionIri, ChangePermissionHasPermissionsApiRequestADM),
      PermissionGetResponseADM,
    ](
      permissionsEndpoints.putPerrmissionsHasPermissions,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionHasPermissionsApiRequestADM) =>
        restService.updatePermissionHasPermissions(permissionIri, request, user)
      },
    )

  private val putPermissionsResourceClass =
    SecuredEndpointHandler[
      (PermissionIri, ChangePermissionResourceClassApiRequestADM),
      DefaultObjectAccessPermissionGetResponseADM,
    ](
      permissionsEndpoints.putPermisssionsResourceClass,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionResourceClassApiRequestADM) =>
        restService.updatePermissionResourceClass(permissionIri, request, user)
      },
    )

  private val putPermissionsProperty =
    SecuredEndpointHandler[
      (PermissionIri, ChangePermissionPropertyApiRequestADM),
      DefaultObjectAccessPermissionGetResponseADM,
    ](
      permissionsEndpoints.putPermissionsProperty,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionPropertyApiRequestADM) =>
        restService.updatePermissionProperty(permissionIri, request, user)
      },
    )

  private val securedHandlers =
    List(
      postPermissionsApHandler,
      getPermissionsApByProjectIriHandler,
      getPermissionsApByProjectAndGroupIriHandler,
      getPermissionsDaopByProjectIriHandler,
      getPermissionsByProjectIriHandler,
      putPermissionsDoapForWhatHandler,
      putPermissionsProjectIriGroupHandler,
      putPermissionsHasPermissionsHandler,
      putPermissionsProperty,
      putPermissionsResourceClass,
      deletePermissionHandler,
      postPermissionsDoapHandler,
    ).map(mapper.mapSecuredEndpointHandler(_))

  val allHanders = securedHandlers
}

object PermissionsEndpointsHandlers {

  val layer = ZLayer.derive[PermissionsEndpointsHandlers]
}
