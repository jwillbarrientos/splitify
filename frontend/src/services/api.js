const API_BASE = '/api';

export async function syncPlaylists() {
  const res = await fetch(`${API_BASE}/playlists/sync`, { method: 'POST' });
  if (!res.ok) throw new Error('Sync failed');
  return res.json();
}

export async function getPlaylists() {
  const res = await fetch(`${API_BASE}/playlists`);
  if (!res.ok) throw new Error('Failed to fetch playlists');
  return res.json();
}

export async function getPlaylist(id) {
  const res = await fetch(`${API_BASE}/playlists/${id}`);
  if (!res.ok) throw new Error('Failed to fetch playlist');
  return res.json();
}

export async function getPlaylistSongs(id) {
  const res = await fetch(`${API_BASE}/playlists/${id}/songs`);
  if (!res.ok) throw new Error('Failed to fetch songs');
  return res.json();
}

export async function deletePlaylists() {
  const res = await fetch(`${API_BASE}/playlists`, { method: 'DELETE' });
  if (!res.ok) throw new Error('Failed to delete playlists');
}

export async function logout() {
  await fetch(`${API_BASE}/auth/logout`, { method: 'POST' });
}
