const API_BASE = '/api';

async function apiFetch(url, options = {}) {
  const res = await fetch(url, options);
  if (res.status === 401 || res.status === 403) {
    window.location.href = '/login';
    throw new Error('Session expired');
  }
  return res;
}

export async function getUserProfile() {
  const res = await apiFetch(`${API_BASE}/user/me`);
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

export async function deleteSplitifyPlaylists(ids) {
  const res = await apiFetch(`${API_BASE}/playlists/splitify`, {
    method: 'DELETE',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(ids),
  });
  if (!res.ok) throw new Error('Failed to delete playlists');
}
