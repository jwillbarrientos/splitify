hecho
- Arreglar login
- Usar base entity
- SpaController expresión regular
- Boton de volver de organizar personalizado sale del modal, en lugar de volver al anterior
- Cambiar como se ve los botones de actualizar cuando se presionan
- Cuando se re sincroniza, si cambio el título o foto de alguna de mis playlist, se reflejará en Spotify(ver si funciona)
- BUG: con playlists personalizadas no toma en cuenta los filtros de las playlists origen
- Cuando Splitify crea una playlist, debe de traer la foto de la playlist también
- Cuando edito el nombre de una playlist creada con Splitify, o el camcio el nombre, se debe de ver reflejado en Splitify
- Permitir escribir el nombre de las playlists que se crearán con los métodos genéricos, y ponerte el nombre por defecto. Ej: * se selecciona por idioma *, de crearán las siguientes playlists: Español(opción de editar), Inglés(opción de editar)
- Permitir poder cambiar la foto o nombre de una playlist desde Splitify, pero SOLO con las playlists creadas con Splitify
- Re sincronizar solo agrega/elimina las músicas que se agregaron/eliminaron en las playlists creadas con Spotify, también refleja cambios si es que cambiaste la foto o nombre de la playlist. Con las playlists creadas con Splitify solo muestran cambios en foto o nombre de la playlist si es que se hizo directamente en Spotify, también se reflejan cambios manuales(agregaron/eliminaron músicas) a las playlists(con la lógica que ya tiene el botón de actualizar de ordenar), el encargado de la lógica de agregar o eliminar músicas de las playlists origen son los botones de actualizar que tienen las mismas
- Pensar mejor el tema de sincronizar y actualizar, como hacerle ver al usuario la diferencia de las mismas, o hay alguna mejor forma de hacerlo?
  pedir actualizar requirements, architecture y readme si es necesario
  hacer un buen mensaje de commit
-  mejorar botones de modal canciones eliminadas restaurar?
- Pedir que ya traiga todas las musicas
- Publicar en linode
- Cuando estabas en la pagina, pero, perdiste la autenticacion, y aprietas cualquier boton, haces cualquier accion, no te redirige a login
- Hacer que cuando seleccionas una opcion de crear por idioma o genero, puedas elegir cuales playlists quieres crear y cuales no

________________________________________________________________________________________________________________________


  REFACTOR
- Hacer que filterType y filterValue sean un solo Enum

- Que pasa si mientras creas una playlist, o entras en el modal de canciones eliminadas, sales del modal con el boton X?
- Si ya nadie usa las musicas, las tiene que eliminar de la base de datos
- Fallo al traer el idioma y genero de Fly de marshmello, es decir, no paso las dos veces
- Que hace resincronizar?
- ChatGPT no identifica bien cuando una musica no tiene letra, y pone ingles(supongo por el titulo de la musica), y muchas veces se equivoca, por ejemplo, con musicas donde el autor es japones, pone que la letra es japones, pero la musica no tiene letra
- Logica de other songs(si las musicas en ese idioma no son mas 5, y no merecen tener su propia playlist) no funciona
- La logica de 10 generos fijos, no funciona bien, en el boton de personalizado, no trae bien, trae generos que no estan en esos 10, trae 30
- Si hay tiempo, agregar compabatibilidad para que se vea bien en celular
