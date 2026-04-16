Cambios que me dijo tio
- Arreglar login
- Usar base entity
- SpaController expresión regular

REFACTOR
- Hacer que filterType y filterValue sean un solo Enum

- Boton de volver de organizar personalizado sale del modal, en lugar de volver al anterior
- Cambiar como se ve los botones de actualizar cuando se presionan
- BUG: con playlists personalizadas no toma en cuenta los filtros de las playlists origen
- Cuando se re sincroniza, si cambio el título o foto de alguna de mis playlist, se reflejará en Spotify(ver si funciona)
- Cuando Splitify crea una playlist, debe de traer la foto de la playlist también
- Cuando edito el nombre de una playlist creada con Splitify, o el camcio el nombre, se debe de ver reflejado en Splitify
- Permitir escribir el nombre de las playlists que se crearán con los métodos genéricos, y ponerte el nombre por defecto. Ej: * se selecciona por idioma *, de crearán las siguientes playlists: Español(opción de editar), Inglés(opción de editar)
- Permitir poder cambiar la foto o nombre de una playlist desde Splitify, pero SOLO con las playlists creadas con Splitify
- Re sincronizar solo agrega/elimina las músicas que se agregaron/eliminaron en las playlists creadas con Spotify, también refleja cambios si es que cambiaste la foto o nombre de la playlist. Con las playlists creadas con Splitify solo muestran cambios en foto o nombre de la playlist si es que se hizo directamente en Spotify, también se reflejan cambios manuales(agregaron/eliminaron músicas) a las playlists(con la lógica que ya tiene el botón de actualizar de ordenar), el encargado de la lógica de agregar o eliminar músicas de las playlists origen son los botones de actualizar que tienen las mismas
- Hacer que se note más al apretar los botones de actualizar de las playlists de Splitify
- Pensar mejor el tema de sincronizar y actualizar, como hacerle ver al usuario la diferencia de las mismas, o hay alguna mejor forma de hacerlo?
- Si hay tiempo, agregar compabatibilidad para que se vea bien en celular
- Publicar en linode