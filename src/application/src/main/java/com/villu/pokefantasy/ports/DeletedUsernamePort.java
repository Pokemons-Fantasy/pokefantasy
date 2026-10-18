package com.villu.pokefantasy.ports;

import java.time.Duration;

/**
 * Nombres de cuentas recién borradas. El JWT de acceso solo lleva el username: si alguien registrase ese nombre
 * mientras sigue vivo un JWT de la cuenta borrada, ese JWT entraría en la cuenta nueva. Se reserva el nombre
 * durante la vida del JWT. Falla cerrado: si no se puede reservar, lanza (y el borrado se deshace); si no se
 * puede consultar, el nombre cuenta como reservado.
 */
public interface DeletedUsernamePort {

    void reserve(String username, Duration ttl);

    boolean isReserved(String username);
}
