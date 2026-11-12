#!/usr/bin/env python3
import os
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import matplotlib.patches as patches
from matplotlib.patches import FancyBboxPatch

output_dir = "/home/harsh/Desktop/CodeNova/ghostnet/docs/figures"
os.makedirs(output_dir, exist_ok=True)

# -------------------------------------------------------------
# FIGURE 1: System Architecture Diagram
# -------------------------------------------------------------
def generate_figure1():
    fig, ax = plt.subplots(figsize=(10, 7), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    # Background
    fig.patch.set_facecolor('#ffffff')

    # Title
    ax.text(50, 96, "Figure 1: GhostNet Host-Client Offline Architecture", 
            ha='center', va='center', fontsize=14, fontweight='bold', color='#1a202c')

    # HOST MACHINE (Container)
    host_box = FancyBboxPatch((4, 28), 54, 63, boxstyle="round,pad=1.2", 
                              ec="#2b6cb0", fc="#ebf8ff", lw=2, linestyle='-')
    ax.add_patch(host_box)
    ax.text(31, 88, "HOST LAPTOP / WORKSTATION (SERVER CORE)", 
            ha='center', va='center', fontsize=11, fontweight='bold', color='#2b6cb0')
    ax.text(31, 85, "OS: Linux / Windows | IP: 10.58.43.240 / 192.168.x.x | Port: 8080", 
            ha='center', va='center', fontsize=8.5, color='#4a5568')

    # Host sub-components
    # 1. Spring Boot Core
    sb_box = FancyBboxPatch((7, 62), 48, 19, boxstyle="round,pad=0.8", ec="#2c5282", fc="#ffffff", lw=1.5)
    ax.add_patch(sb_box)
    ax.text(31, 78, "Java 21 / Spring Boot 3 Application Layer", ha='center', va='center', fontsize=10, fontweight='bold', color='#2c5282')
    ax.text(31, 73, "• Embedded Tomcat Web Server (Port 8080)\n• WebSocket Handler & Session Connection Manager\n• RESTful Controllers (Auth, Rooms, Messages, Files, Admin)\n• SecurityContext & PBKDF2/BCrypt Auth Filters", 
            ha='center', va='center', fontsize=7.8, color='#2d3748')

    # 2. Database & Storage
    db_box = FancyBboxPatch((7, 44), 22, 15, boxstyle="round,pad=0.8", ec="#276749", fc="#f0fff4", lw=1.2)
    ax.add_patch(db_box)
    ax.text(18, 55, "H2 Database Engine", ha='center', va='center', fontsize=8.5, fontweight='bold', color='#276749')
    ax.text(18, 49, "• Embedded File-Mode DB\n• User & Room Records\n• Encrypted Blobs / Transcripts", ha='center', va='center', fontsize=7, color='#2f855a')

    # 3. File Storage Repository
    fs_box = FancyBboxPatch((32, 44), 23, 15, boxstyle="round,pad=0.8", ec="#7b341e", fc="#fffaf0", lw=1.2)
    ax.add_patch(fs_box)
    ax.text(43.5, 55, "Local File Storage", ha='center', va='center', fontsize=8.5, fontweight='bold', color='#7b341e')
    ax.text(43.5, 49, "• Uploaded Chat Files\n• Private Vault Enclaves\n• SHA-256 Checksums", ha='center', va='center', fontsize=7, color='#9c4221')

    # 4. Local GPU LLM Engine (Ollama Integration)
    llm_box = FancyBboxPatch((7, 30), 48, 12, boxstyle="round,pad=0.8", ec="#6b46c1", fc="#faf5ff", lw=1.5)
    ax.add_patch(llm_box)
    ax.text(31, 38, "Local Hardware GPU / Ollama AI Daemon", ha='center', va='center', fontsize=9, fontweight='bold', color='#6b46c1')
    ax.text(31, 33, "Host GPU Acceleration (CUDA / Metal / ROCm) | Local GGUF Model (Llama-3-8B / Mistral)\nZero-Cloud Reasoning Engine serving private room queries via internal HTTP (11434)", ha='center', va='center', fontsize=7, color='#553c9a')

    # NETWORK FABRIC (Middle Divider)
    net_box = FancyBboxPatch((62, 28), 12, 63, boxstyle="round,pad=0.8", ec="#c05621", fc="#fffaf0", lw=1.5, linestyle='--')
    ax.add_patch(net_box)
    ax.text(68, 62, "OFFLINE\nLOCAL\nNETWORK\nFABRIC\n\nNo Internet\nRequired\n\nWi-Fi Hotspot\n/ University\nLAN\n(10.x.x.x)", 
            ha='center', va='center', fontsize=8.5, fontweight='bold', color='#c05621')

    # CLIENT DEVICES
    client_box = FancyBboxPatch((78, 28), 18, 63, boxstyle="round,pad=1.2", ec="#319795", fc="#e6fffa", lw=2)
    ax.add_patch(client_box)
    ax.text(87, 88, "CONNECTED CLIENTS\n(BROWSER-ONLY)", ha='center', va='center', fontsize=9.5, fontweight='bold', color='#234e52')

    # Client 1: Mobile Phone
    c1 = FancyBboxPatch((80, 68), 14, 15, boxstyle="round,pad=0.6", ec="#319795", fc="#ffffff", lw=1.2)
    ax.add_patch(c1)
    ax.text(87, 78, "Mobile Browser\n(Android / iOS)", ha='center', va='center', fontsize=8, fontweight='bold', color='#285e61')
    ax.text(87, 72, "Ephemeral Guest\nor Operative Auth", ha='center', va='center', fontsize=7, color='#4a5568')

    # Client 2: Secondary PC / Laptop
    c2 = FancyBboxPatch((80, 49), 14, 15, boxstyle="round,pad=0.6", ec="#319795", fc="#ffffff", lw=1.2)
    ax.add_patch(c2)
    ax.text(87, 59, "Secondary Laptop\n(Chrome / Firefox)", ha='center', va='center', fontsize=8, fontweight='bold', color='#285e61')
    ax.text(87, 53, "Full Operative &\nVault Encryption", ha='center', va='center', fontsize=7, color='#4a5568')

    # Client 3: Guest Device
    c3 = FancyBboxPatch((80, 31), 14, 14, boxstyle="round,pad=0.6", ec="#319795", fc="#ffffff", lw=1.2)
    ax.add_patch(c3)
    ax.text(87, 40, "Temporary Node\n(Tablet / Guest)", ha='center', va='center', fontsize=8, fontweight='bold', color='#285e61')
    ax.text(87, 34, "Device Credential\nRestoration", ha='center', va='center', fontsize=7, color='#4a5568')

    # Connecting Arrows
    arrow_props = dict(arrowstyle="<->", color="#4a5568", lw=2)
    ax.annotate("", xy=(58, 71), xytext=(62, 71), arrowprops=arrow_props)
    ax.annotate("", xy=(74, 71), xytext=(78, 71), arrowprops=arrow_props)

    ax.annotate("", xy=(58, 52), xytext=(62, 52), arrowprops=arrow_props)
    ax.annotate("", xy=(74, 52), xytext=(78, 52), arrowprops=arrow_props)

    ax.annotate("", xy=(58, 36), xytext=(62, 36), arrowprops=arrow_props)
    ax.annotate("", xy=(74, 36), xytext=(78, 36), arrowprops=arrow_props)

    # Footnote labels
    ax.text(50, 20, "Inter-Node Communication: HTTP REST (APIs & Chunks) + Full-Duplex WebSockets (Real-Time Streams)\nZero External Cloud Endpoints | Zero Third-Party SaaS | 100% Self-Contained Local Execution", 
            ha='center', va='center', fontsize=8, style='italic', color='#4a5568')

    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "figure1_architecture.png"), dpi=300, bbox_inches='tight')
    plt.close()

# -------------------------------------------------------------
# FIGURE 2: Application Flowchart
# -------------------------------------------------------------
def generate_figure2():
    fig, ax = plt.subplots(figsize=(9, 10), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')

    fig.patch.set_facecolor('#ffffff')

    ax.text(50, 97, "Figure 2: Application Workflow and User Onboarding Flowchart", 
            ha='center', va='center', fontsize=13, fontweight='bold', color='#1a202c')

    def draw_node(x, y, w, h, text, subtext="", shape="round", color="#2b6cb0", bg="#ebf8ff"):
        box = FancyBboxPatch((x - w/2, y - h/2), w, h, boxstyle=f"{shape},pad=0.5", ec=color, fc=bg, lw=1.5)
        ax.add_patch(box)
        if subtext:
            ax.text(x, y + 1.2, text, ha='center', va='center', fontsize=8.5, fontweight='bold', color=color)
            ax.text(x, y - 1.8, subtext, ha='center', va='center', fontsize=7, color='#4a5568')
        else:
            ax.text(x, y, text, ha='center', va='center', fontsize=8.5, fontweight='bold', color=color)

    def draw_arrow(x1, y1, x2, y2, label=""):
        ax.annotate("", xy=(x2, y2), xytext=(x1, y1), arrowprops=dict(arrowstyle="->", color="#2d3748", lw=1.5))
        if label:
            mx, my = (x1 + x2)/2, (y1 + y2)/2
            ax.text(mx + 2, my, label, fontsize=7.5, fontweight='bold', color='#c53030')

    # Step 1: Launch
    draw_node(50, 91, 46, 5, "USER LAUNCHES BROWSER", "Opens http://<host-ip>:8080/ on local network", "round", "#2c5282", "#ebf8ff")

    # Step 2: Auth Check
    draw_node(50, 81, 44, 6, "DECISION: Existing Session Token?", "Checked against browser localStorage", "square", "#d69e2e", "#fffff0")
    draw_arrow(50, 88.5, 50, 84)

    # Branch Left: Yes (Valid Session)
    # Branch Right: No (Unauthenticated)
    draw_arrow(30, 81, 22, 81, "Yes")
    draw_node(18, 71, 30, 6, "RESTORE SESSION", "Validate Token with Host DB", "round", "#2b6cb0", "#ebf8ff")
    draw_arrow(20, 78, 20, 74)

    draw_arrow(70, 81, 78, 81, "No")
    draw_node(80, 71, 34, 6, "CHOOSE ACCESS LEVEL", "1-Click Guest or Registered Operative", "square", "#c05621", "#fffaf0")
    draw_arrow(80, 78, 80, 74)

    # Sub-branch under Access Level
    draw_node(65, 59, 28, 6, "JOIN AS GUEST", "Enter Display Name; Token & Device Cred Issued", "round", "#dd6b20", "#fffaf0")
    draw_node(92, 59, 22, 6, "LOGIN / REGISTER", "PBKDF2/BCrypt Credentials", "round", "#319795", "#e6fffa")
    draw_arrow(75, 68, 67, 62)
    draw_arrow(85, 68, 90, 62)

    # Convergence: Active User
    draw_node(50, 48, 54, 6, "USER CONTEXT INITIALIZED & WEBSOCKET CONNECTED", "WebSocket Handshake authenticated (?token=...); Online Presence Activated", "round", "#276749", "#f0fff4")
    draw_arrow(18, 68, 30, 51)
    draw_arrow(65, 56, 55, 51)
    draw_arrow(92, 56, 68, 51)

    # Auto-enrollment
    draw_node(50, 38, 50, 6, "AUTO-ENROLL & SELECT #GENERAL MESH (GN-MAIN)", "Load paginated room history; listen to incoming broadcasts", "round", "#2b6cb0", "#ebf8ff")
    draw_arrow(50, 45, 50, 41)

    # Core User Actions
    draw_node(20, 26, 26, 8, "ENCLAVE ROOMS\n& MESSAGING", "• Join room by code\n• Create private enclave\n• Real-time broadcast\n• Offline background sync", "round", "#2c5282", "#f7fafc")
    draw_node(50, 26, 26, 8, "FILE TRANSFERS\n& MEDIA", "• Share file in room\n• Chunked upload\n• Image preview\n• SHA-256 verification", "round", "#7b341e", "#f7fafc")
    draw_node(80, 26, 26, 8, "PRIVATE VAULT\n(OPERATIVES ONLY)", "• Client-side PBKDF2\n• AES-GCM-256 in RAM\n• Zero-knowledge host\n• Guests restricted", "round", "#6b46c1", "#f7fafc")

    draw_arrow(40, 35, 25, 30)
    draw_arrow(50, 35, 50, 30)
    draw_arrow(60, 35, 75, 30)

    # Future Core Feature: Local LLM Querying
    draw_node(50, 13, 76, 7, "OFFLINE AI REASONING ENCLAVE (UPCOMING CORE UPGRADE)", "User asks question via '@ai <query>' -> Host laptop GPU executes Ollama offline inference -> Result shared to room", "round", "#b83280", "#fff5f7")
    draw_arrow(50, 22, 50, 16.5)

    # Exit
    draw_node(50, 4, 36, 4.5, "DISCONNECT / PURGE CREDENTIALS", "Purge guest device or logout operative", "round", "#718096", "#edf2f7")
    draw_arrow(50, 9.5, 50, 6.25)

    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "figure2_flowchart_app.png"), dpi=300, bbox_inches='tight')
    plt.close()

# -------------------------------------------------------------
# FIGURE 3: Vault Cryptography Flowchart
# -------------------------------------------------------------
def generate_figure3():
    fig, ax = plt.subplots(figsize=(9, 7), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')
    fig.patch.set_facecolor('#ffffff')

    ax.text(50, 96, "Figure 3: Zero-Knowledge Client-Side Vault Cryptographic Flowchart", 
            ha='center', va='center', fontsize=13, fontweight='bold', color='#1a202c')

    # CLIENT SIDE
    c_box = FancyBboxPatch((3, 10), 44, 82, boxstyle="round,pad=1.0", ec="#6b46c1", fc="#faf5ff", lw=1.8)
    ax.add_patch(c_box)
    ax.text(25, 89, "CLIENT-SIDE (BROWSER RAM)", ha='center', va='center', fontsize=11, fontweight='bold', color='#6b46c1')
    ax.text(25, 86, "Private Keys & Plaintext Never Leave Client", ha='center', va='center', fontsize=8, color='#553c9a')

    # HOST SIDE
    h_box = FancyBboxPatch((53, 10), 44, 82, boxstyle="round,pad=1.0", ec="#276749", fc="#f0fff4", lw=1.8)
    ax.add_patch(h_box)
    ax.text(75, 89, "HOST SERVER (STORAGE LAYER)", ha='center', va='center', fontsize=11, fontweight='bold', color='#276749')
    ax.text(75, 86, "Zero-Knowledge Blind Storage (Ciphertext Only)", ha='center', va='center', fontsize=8, color='#2f855a')

    # Step 1: User enters passphrase
    b1 = FancyBboxPatch((7, 73), 36, 8, boxstyle="round,pad=0.5", ec="#805ad5", fc="#ffffff", lw=1.2)
    ax.add_patch(b1)
    ax.text(25, 78, "Step 1: Operative Enters Master Passphrase", ha='center', va='center', fontsize=8, fontweight='bold', color='#44337a')
    ax.text(25, 75, "High-entropy user secret input in browser UI", ha='center', va='center', fontsize=7, color='#718096')

    # Step 2: Key Derivation
    b2 = FancyBboxPatch((7, 58), 36, 11, boxstyle="round,pad=0.5", ec="#805ad5", fc="#ffffff", lw=1.2)
    ax.add_patch(b2)
    ax.text(25, 66, "Step 2: PBKDF2 Key Derivation", ha='center', va='center', fontsize=8.5, fontweight='bold', color='#44337a')
    ax.text(25, 61, "• Algorithm: PBKDF2 with HMAC-SHA256\n• Iteration Count: 100,000 Rounds\n• Salt: 16-Byte Cryptographically Random Salt\n• Output: 256-bit AES Master Key in RAM", 
            ha='center', va='center', fontsize=7, color='#4a5568')

    # Step 3: Encryption
    b3 = FancyBboxPatch((7, 40), 36, 14, boxstyle="round,pad=0.5", ec="#805ad5", fc="#ffffff", lw=1.2)
    ax.add_patch(b3)
    ax.text(25, 50, "Step 3: AES-GCM-256 Symmetric Encryption", ha='center', va='center', fontsize=8.5, fontweight='bold', color='#44337a')
    ax.text(25, 44, "• SubtleCrypto Web API Execution\n• Fresh 12-Byte IV Generated per File\n• 128-bit Authentication Tag Appended\n• SHA-256 Integrity Hash Calculated", 
            ha='center', va='center', fontsize=7, color='#4a5568')

    # Step 4: Transfer
    b4 = FancyBboxPatch((7, 24), 36, 11, boxstyle="round,pad=0.5", ec="#805ad5", fc="#ffffff", lw=1.2)
    ax.add_patch(b4)
    ax.text(25, 32, "Step 4: Encrypted Blob Packaging", ha='center', va='center', fontsize=8.5, fontweight='bold', color='#44337a')
    ax.text(25, 27, "Payload: [Salt (16B) || IV (12B) || Ciphertext || AuthTag (16B)]\nPlaintext discarded immediately from client RAM", 
            ha='center', va='center', fontsize=7, color='#4a5568')

    # Host Operations
    hb1 = FancyBboxPatch((57, 54), 36, 16, boxstyle="round,pad=0.5", ec="#38a169", fc="#ffffff", lw=1.2)
    ax.add_patch(hb1)
    ax.text(75, 65, "Host Storage & Indexing", ha='center', va='center', fontsize=8.5, fontweight='bold', color='#22543d')
    ax.text(75, 59, "• Verifies User Quota & Active Operative Session\n• Stores Ciphertext File to Disk Enclave\n• Records Metadata in H2 (Filename, Size, IV, Salt, Hash)\n• Host has NO KEY to decrypt contents", 
            ha='center', va='center', fontsize=7, color='#2d3748')

    hb2 = FancyBboxPatch((57, 24), 36, 14, boxstyle="round,pad=0.5", ec="#38a169", fc="#ffffff", lw=1.2)
    ax.add_patch(hb2)
    ax.text(75, 33, "Zero-Knowledge Retrieval", ha='center', va='center', fontsize=8.5, fontweight='bold', color='#22543d')
    ax.text(75, 27, "• User requests file download via auth token\n• Host streams raw encrypted blob to client\n• Client re-derives key using Passphrase + Salt\n• Decrypts AES-GCM in RAM and saves locally", 
            ha='center', va='center', fontsize=7, color='#2d3748')

    # Connecting Arrows
    ax.annotate("", xy=(25, 69), xytext=(25, 73), arrowprops=dict(arrowstyle="->", color="#44337a", lw=1.5))
    ax.annotate("", xy=(25, 54), xytext=(25, 58), arrowprops=dict(arrowstyle="->", color="#44337a", lw=1.5))
    ax.annotate("", xy=(25, 35), xytext=(25, 40), arrowprops=dict(arrowstyle="->", color="#44337a", lw=1.5))

    ax.annotate("", xy=(57, 62), xytext=(43, 29), arrowprops=dict(arrowstyle="->", color="#c53030", lw=1.8, linestyle='--'))
    ax.text(50, 48, "POST /api/vault/upload\n(Ciphertext Only)", ha='center', va='center', fontsize=7.5, fontweight='bold', color='#c53030')

    ax.annotate("", xy=(43, 27), xytext=(57, 27), arrowprops=dict(arrowstyle="->", color="#2b6cb0", lw=1.8))
    ax.text(50, 24, "GET /api/vault/download", ha='center', va='center', fontsize=7.5, fontweight='bold', color='#2b6cb0')

    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "figure3_flowchart_crypto.png"), dpi=300, bbox_inches='tight')
    plt.close()

# -------------------------------------------------------------
# FIGURE 4: Host GPU LLM Inference Architecture
# -------------------------------------------------------------
def generate_figure4():
    fig, ax = plt.subplots(figsize=(10, 7.5), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')
    fig.patch.set_facecolor('#ffffff')

    ax.text(50, 96, "Figure 4: Host Laptop GPU-Accelerated Offline LLM Inference Pipeline", 
            ha='center', va='center', fontsize=13, fontweight='bold', color='#1a202c')

    # Client Node (Mobile or Laptop)
    c_box = FancyBboxPatch((3, 25), 24, 62, boxstyle="round,pad=0.8", ec="#319795", fc="#e6fffa", lw=1.5)
    ax.add_patch(c_box)
    ax.text(15, 82, "CLIENT BROWSER\n(Mobile or Laptop)", ha='center', va='center', fontsize=9.5, fontweight='bold', color='#234e52')
    ax.text(15, 68, "Operative enters\nquery in room:\n'@ai how to fix\nnetwork timeout?'\n\nOr opens dedicated\nAI Reasoning Panel", 
            ha='center', va='center', fontsize=8, color='#285e61')
    ax.text(15, 45, "Client Transmits\nvia WebSocket:\nFRAME: ROOM_MSG\n(content: '@ai...')", 
            ha='center', va='center', fontsize=7.8, fontweight='bold', color='#2c5282')
    ax.text(15, 31, "Receives streamed\ntokens or finalized\nsolution in room", 
            ha='center', va='center', fontsize=7.5, color='#4a5568')

    # GhostNet Core (Host Backend)
    s_box = FancyBboxPatch((32, 25), 32, 62, boxstyle="round,pad=0.8", ec="#2b6cb0", fc="#ebf8ff", lw=1.5)
    ax.add_patch(s_box)
    ax.text(48, 82, "GHOSTNET SERVER CORE\n(Java 21 Spring Boot)", ha='center', va='center', fontsize=9.5, fontweight='bold', color='#2c5282')
    
    # Components inside backend
    s1 = FancyBboxPatch((34, 62), 28, 14, boxstyle="round,pad=0.5", ec="#2b6cb0", fc="#ffffff", lw=1.2)
    ax.add_patch(s1)
    ax.text(48, 71, "WebSocket Parser & Filter", ha='center', va='center', fontsize=8, fontweight='bold', color='#2b6cb0')
    ax.text(48, 66, "Detects '@ai' tag\nValidates Operative Permissions\nSanitizes prompt input", ha='center', va='center', fontsize=7, color='#4a5568')

    s2 = FancyBboxPatch((34, 42), 28, 16, boxstyle="round,pad=0.5", ec="#2b6cb0", fc="#ffffff", lw=1.2)
    ax.add_patch(s2)
    ax.text(48, 52, "Local LLM Inference Bridge", ha='center', va='center', fontsize=8, fontweight='bold', color='#2b6cb0')
    ax.text(48, 46, "Dispatches HTTP request to\nhttp://localhost:11434/api/generate\nBuffers or streams chunked\nresponse tokens asynchronously", ha='center', va='center', fontsize=7, color='#4a5568')

    s3 = FancyBboxPatch((34, 28), 28, 10, boxstyle="round,pad=0.5", ec="#2b6cb0", fc="#ffffff", lw=1.2)
    ax.add_patch(s3)
    ax.text(48, 34, "Room Broadcast Engine", ha='center', va='center', fontsize=8, fontweight='bold', color='#2b6cb0')
    ax.text(48, 30, "Publishes AI solution to Room", ha='center', va='center', fontsize=7, color='#4a5568')

    # Host GPU & Ollama Layer
    g_box = FancyBboxPatch((69, 25), 28, 62, boxstyle="round,pad=0.8", ec="#6b46c1", fc="#faf5ff", lw=1.5)
    ax.add_patch(g_box)
    ax.text(83, 82, "HOST HARDWARE &\nOLLAMA SERVICE", ha='center', va='center', fontsize=9.5, fontweight='bold', color='#553c9a')

    g1 = FancyBboxPatch((71, 62), 24, 14, boxstyle="round,pad=0.5", ec="#805ad5", fc="#ffffff", lw=1.2)
    ax.add_patch(g1)
    ax.text(83, 71, "Ollama Local Service", ha='center', va='center', fontsize=8, fontweight='bold', color='#6b46c1')
    ax.text(83, 66, "Listening on 127.0.0.1:11434\nModel: Llama-3-8B-Q4_K_M\nor Mistral-7B-Instruct", ha='center', va='center', fontsize=7, color='#4a5568')

    g2 = FancyBboxPatch((71, 35), 24, 22, boxstyle="round,pad=0.5", ec="#805ad5", fc="#ffffff", lw=1.2)
    ax.add_patch(g2)
    ax.text(83, 50, "Host Laptop GPU", ha='center', va='center', fontsize=8.5, fontweight='bold', color='#6b46c1')
    ax.text(83, 42, "Dedicated VRAM Execution\n(NVIDIA CUDA / Vulkan)\nZero Internet Dependency\n100% Offline Inference\nPrivate & Air-Gapped", ha='center', va='center', fontsize=7, color='#4a5568')

    # Arrows between blocks
    ax.annotate("", xy=(34, 70), xytext=(27, 70), arrowprops=dict(arrowstyle="->", color="#2c5282", lw=1.8))
    ax.text(30.5, 73, "WS", fontsize=7.5, fontweight='bold', color='#2c5282')

    ax.annotate("", xy=(71, 68), xytext=(62, 53), arrowprops=dict(arrowstyle="->", color="#6b46c1", lw=1.8))
    ax.text(66, 62, "HTTP POST\n(Prompt)", fontsize=7, fontweight='bold', color='#6b46c1')

    ax.annotate("", xy=(83, 57), xytext=(83, 62), arrowprops=dict(arrowstyle="->", color="#805ad5", lw=1.5))

    ax.annotate("", xy=(62, 45), xytext=(71, 64), arrowprops=dict(arrowstyle="->", color="#38a169", lw=1.8, linestyle='--'))
    ax.text(66, 52, "Tokens\nStream", fontsize=7, fontweight='bold', color='#38a169')

    ax.annotate("", xy=(27, 33), xytext=(34, 33), arrowprops=dict(arrowstyle="->", color="#2c5282", lw=1.8))
    ax.text(30.5, 36, "WS Frame", fontsize=7, fontweight='bold', color='#2c5282')

    # Note at bottom
    ax.text(50, 16, "Key Advantage: Client phones and low-power devices tap into the Host Laptop's raw GPU compute power\nwithout installing any local weights, enabling complex reasoning, code troubleshooting, and offline assistance.", 
            ha='center', va='center', fontsize=8, style='italic', color='#2d3748')

    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "figure4_flowchart_llm.png"), dpi=300, bbox_inches='tight')
    plt.close()

# -------------------------------------------------------------
# FIGURE 5: Network Diagnostics & Subnet Discovery
# -------------------------------------------------------------
def generate_figure5():
    fig, ax = plt.subplots(figsize=(9, 6), dpi=300)
    ax.set_xlim(0, 100)
    ax.set_ylim(0, 100)
    ax.axis('off')
    fig.patch.set_facecolor('#ffffff')

    ax.text(50, 95, "Figure 5: GhostNet Multi-Interface Network Diagnostics & Discovery Matrix", 
            ha='center', va='center', fontsize=12, fontweight='bold', color='#1a202c')

    # Host Interfaces Card
    b_if = FancyBboxPatch((5, 45), 42, 42, boxstyle="round,pad=0.8", ec="#2b6cb0", fc="#ebf8ff", lw=1.5)
    ax.add_patch(b_if)
    ax.text(26, 82, "HOST NETWORK INTERFACES", ha='center', va='center', fontsize=9.5, fontweight='bold', color='#2b6cb0')
    ax.text(26, 64, "• wlan0 (Wireless LAN): 10.58.43.240 [PRIMARY]\n• docker0 (Virtual Bridge): 172.17.0.1\n• br-0365 (Container Bridge): 172.20.0.1\n• lo (Loopback): 127.0.0.1\n\nAuto-detected via NetworkInterface.getNetworkInterfaces()\nBinds to 0.0.0.0:8080 for universal reachability", 
            ha='center', va='center', fontsize=7.5, color='#2d3748')

    # Client Connection Card
    b_conn = FancyBboxPatch((53, 45), 42, 42, boxstyle="round,pad=0.8", ec="#276749", fc="#f0fff4", lw=1.5)
    ax.add_patch(b_conn)
    ax.text(74, 82, "LOCAL DISCOVERY & REACHABILITY", ha='center', va='center', fontsize=9.5, fontweight='bold', color='#276749')
    ax.text(74, 64, "• Host Access URL: http://10.58.43.240:8080/\n• QR Code generation for instant phone joining\n• Subnet broadcast & diagnostic ping\n• Operating Modes:\n  1. Pure Air-Gapped Mode (Hotspot only)\n  2. Hybrid Mode (Local LAN + Internet present)\n  3. University Wi-Fi Ad-Hoc Access", 
            ha='center', va='center', fontsize=7.5, color='#2d3748')

    # Diagnostic Table
    tb = FancyBboxPatch((5, 12), 90, 27, boxstyle="round,pad=0.8", ec="#4a5568", fc="#f7fafc", lw=1.2)
    ax.add_patch(tb)
    ax.text(50, 34, "DIAGNOSTIC TELEMETRY & HEARTBEAT SYSTEM", ha='center', va='center', fontsize=9, fontweight='bold', color='#2d3748')
    ax.text(50, 22, "• WebSocket Heartbeat Ping/Pong every 25 seconds; automatic eviction after 60s idle\n• Client Fallback HTTP Polling at 2000ms ensures zero dropped messages during packet loss\n• Host Resource Telemetry: Heap utilization, JVM Thread Count, Disk I/O Quota Tracking\n• Active Operative Directory: Distinction between Authenticated Members and Ephemeral Guests", 
            ha='center', va='center', fontsize=7.5, color='#4a5568')

    plt.tight_layout()
    plt.savefig(os.path.join(output_dir, "figure5_network_matrix.png"), dpi=300, bbox_inches='tight')
    plt.close()

if __name__ == "__main__":
    generate_figure1()
    generate_figure2()
    generate_figure3()
    generate_figure4()
    generate_figure5()
    print("All 5 diagrams successfully generated in", output_dir)
