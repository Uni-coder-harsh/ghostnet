/**
 * GhostNet Host Administration Dashboard Controller (Req 246-261).
 */
(function() {
  'use strict';

  let pollInterval = null;

  // DOM Elements
  const adminHostIp = document.getElementById('adminHostIp');
  const adminUptime = document.getElementById('adminUptime');
  const adminShutdownBtn = document.getElementById('adminShutdownBtn');

  const mServerStatus = document.getElementById('mServerStatus');
  const mVersion = document.getElementById('mVersion');
  const mOnlineUsers = document.getElementById('mOnlineUsers');
  const mRegisteredUsers = document.getElementById('mRegisteredUsers');
  const mActiveWs = document.getElementById('mActiveWs');
  const mActiveRooms = document.getElementById('mActiveRooms');
  const mTotalMessages = document.getElementById('mTotalMessages');
  const mDmCount = document.getElementById('mDmCount');
  const mRoomMsgCount = document.getElementById('mRoomMsgCount');
  const mMemoryPercent = document.getElementById('mMemoryPercent');
  const mMemoryUsed = document.getElementById('mMemoryUsed');
  const mMemoryMax = document.getElementById('mMemoryMax');

  const triggerBackupBtn = document.getElementById('triggerBackupBtn');
  const triggerRetentionBtn = document.getElementById('triggerRetentionBtn');
  const refreshAdminBtn = document.getElementById('refreshAdminBtn');
  const maintenanceFeedback = document.getElementById('maintenanceFeedback');

  const sessionsTableBody = document.getElementById('sessionsTableBody');
  const sessionCount = document.getElementById('sessionCount');
  const networkInterfacesContainer = document.getElementById('networkInterfacesContainer');

  function injectIcons() {
    if (typeof GhostNetIcons === 'undefined') return;
    const map = {
      adminBrandIcon: GhostNetIcons.shield,
      powerIcon: GhostNetIcons.logout,
      telemetryIcon: GhostNetIcons.activity,
      backupIcon: GhostNetIcons.server,
      retentionIcon: GhostNetIcons.trash,
      refreshIcon: GhostNetIcons.refresh
    };
    for (const [id, svg] of Object.entries(map)) {
      const el = document.getElementById(id);
      if (el) el.innerHTML = svg;
    }
  }

  function showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      toast.style.transition = 'all 0.2s';
      setTimeout(() => toast.remove(), 250);
    }, 3500);
  }

  async function checkAdminAuth() {
    try {
      const res = await fetch('/api/auth/me');
      if (!res.ok) {
        location.href = '/';
        return;
      }
      const user = await res.json();
      if (user.role !== 'ROLE_ADMIN') {
        alert('Access Denied: Host Administrator privileges (ROLE_ADMIN) required.');
        location.href = '/';
        return;
      }
      loadAllAdminData();
      startPolling();
    } catch (e) {
      location.href = '/';
    }
  }

  function startPolling() {
    clearInterval(pollInterval);
    pollInterval = setInterval(loadMetrics, 4000);
  }

  async function loadAllAdminData() {
    await Promise.all([
      loadMetrics(),
      loadSessions(),
      loadNetworkInterfaces()
    ]);
  }

  async function loadMetrics() {
    try {
      const res = await fetch('/api/admin/metrics');
      if (!res.ok) return;
      const m = await res.json();

      adminHostIp.textContent = `${m.hostIp}:${m.port}`;
      adminUptime.textContent = m.formattedUptime;

      mServerStatus.textContent = m.status;
      mVersion.textContent = `GhostNet v${m.version}`;
      mOnlineUsers.textContent = m.onlineUsersCount;
      mRegisteredUsers.textContent = m.registeredUsersCount;
      mActiveWs.textContent = m.activeWebSocketConnections;
      mActiveRooms.textContent = m.activeRoomsCount;
      mTotalMessages.textContent = m.totalMessagesProcessed;
      mDmCount.textContent = m.directMessagesCount;
      mRoomMsgCount.textContent = m.roomMessagesCount;
      mMemoryPercent.textContent = `${m.memoryUsagePercent}%`;
      mMemoryUsed.textContent = m.memoryUsedMb;
      mMemoryMax.textContent = m.memoryMaxMb;
    } catch (e) {
      console.warn('Failed to fetch telemetry:', e);
    }
  }

  async function loadSessions() {
    try {
      const res = await fetch('/api/admin/sessions');
      if (!res.ok) return;
      const sessions = await res.json();
      renderSessions(sessions);
    } catch (e) {
      console.warn('Failed to load sessions:', e);
    }
  }

  function renderSessions(sessions) {
    sessionCount.textContent = sessions.length.toString();
    sessionsTableBody.innerHTML = '';

    if (sessions.length === 0) {
      sessionsTableBody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--text-dim);">No active sessions found.</td></tr>`;
      return;
    }

    sessions.forEach(s => {
      const tr = document.createElement('tr');
      const createdStr = new Date(s.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      const expiresStr = new Date(s.expiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

      tr.innerHTML = `
        <td>
          <div style="font-weight: 600;">${escapeHtml(s.displayName)}</div>
          <div style="font-size: 0.72rem; color: var(--text-dim);">${escapeHtml(s.username)}</div>
        </td>
        <td style="font-family: var(--font-mono); color: var(--neon-cyan);">${escapeHtml(s.clientIp)}</td>
        <td style="font-size: 0.75rem; color: var(--text-muted); max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(s.userAgent)}">
          ${escapeHtml(s.userAgent)}
        </td>
        <td>${createdStr}</td>
        <td>${expiresStr}</td>
        <td>
          <button class="btn btn-danger btn-sm revoke-btn" data-token="${s.token}">Revoke</button>
        </td>
      `;

      const revokeBtn = tr.querySelector('.revoke-btn');
      revokeBtn.addEventListener('click', async () => {
        if (confirm(`Revoke session for ${s.displayName}? The user will be immediately disconnected.`)) {
          await revokeSession(s.token);
        }
      });

      sessionsTableBody.appendChild(tr);
    });
  }

  async function revokeSession(token) {
    try {
      const res = await fetch(`/api/admin/sessions/${token}`, { method: 'DELETE' });
      if (res.ok) {
        showToast('Session revoked', 'success');
        loadSessions();
      } else {
        showToast('Failed to revoke session', 'error');
      }
    } catch (e) {
      showToast('Error revoking session', 'error');
    }
  }

  async function loadNetworkInterfaces() {
    try {
      const res = await fetch('/api/admin/network');
      if (!res.ok) return;
      const interfaces = await res.json();
      renderNetworkInterfaces(interfaces);
    } catch (e) {
      console.warn('Failed to load network interfaces:', e);
    }
  }

  function renderNetworkInterfaces(interfaces) {
    networkInterfacesContainer.innerHTML = '';
    interfaces.forEach(ni => {
      const div = document.createElement('div');
      div.className = 'item-card';
      div.innerHTML = `
        <div class="item-info">
          <div class="avatar" style="color: ${ni.primary ? 'var(--neon-green)' : 'var(--neon-cyan)'}">IP</div>
          <div class="item-details">
            <div class="item-name">${escapeHtml(ni.displayName)} (${escapeHtml(ni.name)}) ${ni.primary ? '<span class="brand-badge" style="margin-left: 6px;">PRIMARY REACHABLE</span>' : ''}</div>
            <div class="item-sub" style="font-family: var(--font-mono); color: var(--neon-cyan);">
              ${ni.ipv4Addresses.join(', ') || 'No IPv4 address assigned'}
            </div>
          </div>
        </div>
        <span class="code-pill">${ni.wireless ? 'WIRELESS / HOTSPOT' : 'INTERFACE'}</span>
      `;
      networkInterfacesContainer.appendChild(div);
    });
  }

  // Backup & Maintenance Triggers
  triggerBackupBtn.addEventListener('click', async () => {
    maintenanceFeedback.style.display = 'block';
    maintenanceFeedback.textContent = 'Generating atomic SQLite database snapshot...';
    try {
      const res = await fetch('/api/admin/backup/create', { method: 'POST' });
      const data = await res.json();
      if (data.success) {
        maintenanceFeedback.textContent = `Snapshot created: ${data.filename} (${data.sizeBytes} bytes)`;
        showToast('Database backup snapshot created', 'success');
      } else {
        maintenanceFeedback.textContent = `Backup failed: ${data.message}`;
        showToast('Backup failed', 'error');
      }
    } catch (e) {
      maintenanceFeedback.textContent = 'Network error while creating backup';
    }
  });

  triggerRetentionBtn.addEventListener('click', async () => {
    maintenanceFeedback.style.display = 'block';
    maintenanceFeedback.textContent = 'Purging historical messages according to retention policy...';
    try {
      const res = await fetch('/api/admin/retention/run', { method: 'POST' });
      const data = await res.json();
      maintenanceFeedback.textContent = `Purge completed: ${data.directMessagesPurged} direct messages and ${data.roomMessagesPurged} room messages deleted.`;
      showToast('Retention purge completed', 'success');
      loadMetrics();
    } catch (e) {
      maintenanceFeedback.textContent = 'Error executing retention cleanup';
    }
  });

  refreshAdminBtn.addEventListener('click', loadAllAdminData);

  adminShutdownBtn.addEventListener('click', async () => {
    const confirmation = prompt('Are you sure you want to shut down the GhostNet server? Type "SHUTDOWN" to confirm:');
    if (confirmation === 'SHUTDOWN') {
      try {
        await fetch('/api/admin/shutdown', { method: 'POST' });
        document.body.innerHTML = `
          <div style="height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; background: #06090e; color: #f8fafc; font-family: sans-serif;">
            <h1 style="color: var(--neon-cyan); margin-bottom: 1rem;">GHOSTNET SERVER SHUT DOWN</h1>
            <p style="color: #94a3b8;">The host process has terminated gracefully. Stored messages and accounts remain intact on disk.</p>
          </div>
        `;
      } catch (e) {
        alert('Server is now shutting down.');
      }
    }
  });

  function escapeHtml(str) {
    if (!str) return '';
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  // Bootstrap
  injectIcons();
  checkAdminAuth();
})();
