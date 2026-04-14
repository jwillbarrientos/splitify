- cambiar mi design pen y cambiar my documento de requerimientos y arquitectura en base a los nuevos cambios
- disenar la base de datos
- intentar integrar el login de spotify (explicame como crearme una cuenta de developer en Spotify para tener acceso a loguearme via OAuth)
- en la pagina principal trame mis playlists de spotify y las 10 primeras musicas de spotify
- agregar un boton a la pagina principal que va a crear una playlist de Splitify y le va a poner 2 de las musicas aleatorias que estan en algunas de las playlists

Arreglar login
Usar base entity
SpaController expresión regular

Boton de volver de organizar personalizado sale del modal, en lugar de volver al anterior

cambiar como se ve el boton de actualizar cuando actualiza

bug, con playlists personalizadas no toma en cuenta los filtros de las playlists origen

- Cuando se re sincroniza, si cambio el título o foto de alguna de mis playlist, se reflejará en Spotify(ver si funciona)
- Cuando Splitify crea una playlist, debe de traer la foto de la playlist también
- Cuando edito el nombre de una playlist creada con Splitify, o el camcio el nombre, se debe de ver reflejado en Splitify
- Permitir escribir el nombre de las playlists que se crearán con los métodos genéricos, y ponerte el nombre por defecto. Ej: * se selecciona por idioma *, de crearán las siguientes playlists: Español(opción de editar), Inglés(opción de editar)
- Permitir poder cambiar la foto o nombre de una playlist desde Splitify, pero SOLO con las playlists creadas con Splitify
- Re sincronizar solo agrega/elimina las músicas que se agregaron/eliminaron en las playlists creadas con Spotify, también refleja cambios si es que cambiaste la foto o nombre de la playlist. Con las playlists creadas con Splitify solo muestran cambios en foto o nombre de la playlist si es que se hizo directamente en Spotify, también se reflejan cambios manuales(agregaron/eliminaron músicas) a las playlists(con la lógica que ya tiene el botón de actualizar de ordenar), el encargado de la lógica de agregar o eliminar músicas de las playlists origen son los botones de actualizar que tienen las mismas
- Hacer que se note más al apretar los botones de actualizar de las playlists de Splitify
- Pensar mejor el tema de sincronizar y actualizar, como hacerle ver al usuario la diferencia de las mismas, o hay alguna mejor forma de hacerlo?
- Si hay tiempo, agregar compabatibilidad para que se vea bien en celular

- (2h)investigar si es realmente posible con los ultimos cambios de la api de spotify lo siguiente:
  - obtener referencias de las musicas (musica id/name/artist name) q existen en todos los playlists del usuario y de los 'liked' musics
  - crear playlists
  - agregar musicas en los playlists
- (10m)compartir repo en github
- (1h)relevamiento de necesidades funcionales, definicion de producto - responde al "que hacemos"
  - ordenar por (idioma, estilo/genero, anho de lanzamiento del original)
- (1h)disenho de UI usando claude code y pencil
- (2h)arquitectura y disenho de software - responde al "como lo hacemos"
- (3d)vibecodear todo ATM (a toda mierda)
- publicar en linode
-- conversacion sobre figma claude --resume 62fb32fd-5ecc-44a9-b4e0-acbafda8ff2b