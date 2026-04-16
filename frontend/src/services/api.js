const API_BASE = '/api';

async function apiFetch(url, options = {}) {
  const res = await fetch(url, options);
  if (res.status === 401 || res.status === 403) {
    window.location.href = '/login';
    throw new Error('Session expired');
  }
  return res;
}

// No usa apiFetch porque esta llamada decide el estado de auth del frontend:
// si devuelve 401/403 queremos manejarlo en el componente (redirigir via React Router),
// no hacer un reload completo con window.location.href.
export async function getUserProfile() {
  const res = await fetch(`${API_BASE}/user/me`);
  if (res.status === 401 || res.status === 403) return null;
  if (!res.ok) throw new Error('Failed to fetch user profile');
  return res.json();
}

export async function syncPlaylists() {
  const res = await apiFetch(`${API_BASE}/playlists/sync`, { method: 'POST' });
  if (!res.ok) throw new Error('Sync failed');
  return res.json();
}

export async function getPlaylists() {
  const res = await apiFetch(`${API_BASE}/playlists`);
  if (!res.ok) throw new Error('Failed to fetch playlists');
  return res.json();
}

export async function getPlaylist(id) {
  const res = await apiFetch(`${API_BASE}/playlists/${id}`);
  if (!res.ok) throw new Error('Failed to fetch playlist');
  return res.json();
}

export async function getPlaylistSongs(id) {
  const res = await apiFetch(`${API_BASE}/playlists/${id}/songs`);
  if (!res.ok) throw new Error('Failed to fetch songs');
  return res.json();
}

export async function deletePlaylists() {
  const res = await apiFetch(`${API_BASE}/playlists`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete playlists');
}

export async function logout() {
  await fetch(`${API_BASE}/auth/logout`, { method: 'POST' });
}

export async function getSplitifyPlaylists() {
  const res = await apiFetch(`${API_BASE}/playlists/splitify`);
  if (!res.ok) throw new Error('Failed to fetch splitify playlists');
  return res.json();
}

export async function deleteSplitifyPlaylist(id) {
  const res = await apiFetch(`${API_BASE}/playlists/splitify/${id}`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete playlist');
}

export async function createOrganizedPlaylists(playlistIds, options) {
  const res = await apiFetch(`${API_BASE}/playlists/create/combined`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      playlistIds,
      byLanguage: options.idioma,
      byGenre: options.genero,
      byReleaseDate: options.fecha,
    }),
  });
  if (!res.ok) throw new Error('Failed to create playlists');
  return res.json();
}

export async function getAvailableFilters(playlistIds) {
  const qs = playlistIds.map(id => `playlistIds=${encodeURIComponent(id)}`).join('&');
  const res = await apiFetch(`${API_BASE}/playlists/available-filters?${qs}`);
  if (!res.ok) throw new Error('Failed to fetch available filters');
  return res.json();
}

export async function createCustomPlaylist({ playlistIds, languages, genres, artists, name }) {
  const res = await apiFetch(`${API_BASE}/playlists/create/custom`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ playlistIds, languages, genres, artists, name }),
  });
  if (!res.ok) {
    let message = 'No se pudo crear la playlist personalizada.';
    try {
      const body = await res.json();
      if (body && body.message) message = body.message;
    } catch {
      // body no es JSON, usar mensaje por defecto
    }
    throw new Error(message);
  }
  return res.json();
}

export async function previewRefresh(id) {
  const res = await apiFetch(`${API_BASE}/playlists/splitify/${id}/refresh/preview`);
  if (!res.ok) throw new Error('Failed to preview refresh');
  return res.json();
}

export async function refreshSplitifyPlaylist(id, restoreRemoved = false) {
  const res = await apiFetch(`${API_BASE}/playlists/splitify/${id}/refresh?restoreRemoved=${restoreRemoved}`, { method: 'PUT' });
  if (res.status === 204) return null;
  if (!res.ok) throw new Error('Failed to refresh playlist');
  return res.json();
}

export async function refreshSplitifyPlaylists(ids, restoreRemoved = false) {
  const res = await apiFetch(`${API_BASE}/playlists/splitify/batch/refresh?restoreRemoved=${restoreRemoved}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(ids),
  });
  if (!res.ok) throw new Error('Failed to refresh playlists');
  return res.json();
}

export async function deleteSplitifyPlaylists(ids) {
  const res = await apiFetch(`${API_BASE}/playlists/splitify`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(ids),
  });
  if (!res.ok) throw new Error('Failed to delete playlists');
}
