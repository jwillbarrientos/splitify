package io.jona.smusic.sorted_music.dto;

// Representa una playlist a crear por el flujo "Organizar por criterios".
// Se usa tanto en la respuesta del preview (nombres default) como en la request final
// (con los nombres posiblemente editados por el usuario).
//   - filterType: "language", "genre" o "releaseDate"
//   - filterValue: el valor (ej: "English", "Rock"); null para releaseDate
//   - name: el nombre a usar al crear (default o editado por el usuario)
public record PlaylistCreateSpec(String filterType, String filterValue, String name) {}
