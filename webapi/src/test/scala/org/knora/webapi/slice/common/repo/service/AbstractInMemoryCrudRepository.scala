/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.repo.service

import zio.*

abstract class AbstractInMemoryCrudRepository[Entity, Id](entities: Ref[Chunk[Entity]], getId: Entity => Id)
    extends CrudRepository[Entity, Id] {

  /**
   * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the entity instance completely.
   *
   * @param entity The entity to be saved.
   * @return the saved entity.
   */
  override def save(entity: Entity): Task[Entity] =
    entities.update(_.appended(entity)).as(entity)

  /**
   * Deletes a given entity.
   *
   * @param entity The entity to be deleted
   */
  override def delete(entity: Entity): Task[Unit] = deleteById(getId(entity))

  /**
   * Deletes the entity with the given id.
   * If the entity is not found in the persistence store it is silently ignored.
   *
   * @param id The identifier to the entity to be deleted
   */
  override def deleteById(id: Id): Task[Unit] = entities.update(_.filterNot(getId(_) == id))

  /**
   * Retrieves an entity by its id.
   *
   * @param id The identifier of type [[Id]].
   * @return the entity with the given id or [[None]] if none found.
   */
  override def findById(id: Id): Task[Option[Entity]] = findOneBy(entity => getId(entity) == id)

  /**
   * Returns all instances of the type.
   *
   * @return all instances of the type.
   */
  override def findAll(): Task[Chunk[Entity]] = entities.get

  def findOneBy(predicate: Entity => Boolean): Task[Option[Entity]] = entities.get.map(_.find(predicate))
  def findBy(predicate: Entity => Boolean): Task[Chunk[Entity]]     = entities.get.map(_.filter(predicate))
}
