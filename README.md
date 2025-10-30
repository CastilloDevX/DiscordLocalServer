# 💬 Chat Concurrente en Java - Sistema Cliente-Servidor

## 📋 Descripción del Proyecto

Sistema de chat en tiempo real desarrollado en Java que permite la comunicación simultánea entre múltiples usuarios mediante conexiones TCP/IP. El proyecto implementa una arquitectura cliente-servidor utilizando sockets y programación concurrente con hilos para manejar múltiples conexiones simultáneas.

---

## 🎯 Requisitos Implementados (10 puntos)

### ✅ 1. Método `start-connection` (2.5 puntos)

**Descripción:** Establece la conexión del cliente con el servidor mediante dirección IP y puerto especificados.

**Ubicación:** Clase `Client.java` - Método `startConnection(String host, int port)`

**Implementación:**
```java
public void startConnection(String host, int port) throws IOException {
    socket = new Socket(host, port);
    in  = new DataInputStream(socket.getInputStream());
    out = new DataOutputStream(socket.getOutputStream());
    System.out.println("Conectado al servidor " + host + ":" + port);
}
```

**Uso desde terminal:**

**Opción 1 - Conexión local (localhost:8080):**
```bash
java Client
```

**Opción 2 - Con nombre de usuario predefinido:**
```bash
java Client MiNombre
```

**Opción 3 - Especificar IP y Puerto personalizados:**
```bash
java Client NombreUsuario 192.168.1.100 8080
```

**Parámetros:**
- `host`: Dirección IP del servidor (ej: `192.168.1.100`, `localhost`)
- `port`: Puerto de escucha del servidor (por defecto: `8080`)

**Funcionamiento:**
1. Crea un socket TCP hacia la dirección IP y puerto especificados
2. Inicializa los streams de entrada (`DataInputStream`) y salida (`DataOutputStream`)
3. Confirma la conexión exitosa al usuario
4. Permite el intercambio bidireccional de datos con el servidor

---

### ✅ 2. Método `change-userName`

**Descripción:** Permite a un usuario cambiar su nombre de usuario durante la sesión activa del chat.

**Ubicación:** Clase `Server.java` - Método `changeUserName(String oldName, String newName, ClientHandler handler)`

**Implementación:**
```java
static synchronized boolean changeUserName(String oldName, String newName, ClientHandler handler) {
    if (newName == null || newName.isBlank() || clients.containsKey(newName)) 
        return false;
    clients.remove(oldName);
    clients.put(newName, handler);
    return true;
}
```

**Comando para el cliente:**
```
/rename NUEVO_NOMBRE
```

**Ejemplos de uso:**
```
/rename JuanPerez
/rename Admin_01
/rename Usuario123
```

**Funcionamiento:**
1. El cliente envía el comando `/rename` seguido del nuevo nombre
2. El servidor valida que el nuevo nombre no esté vacío ni en uso
3. Actualiza el nombre en el mapa de clientes (`ConcurrentHashMap`)
4. Notifica al usuario del cambio exitoso
5. Envía un mensaje global informando el cambio a todos los participantes
6. Registra la acción en el log del servidor con timestamp

**Validaciones:**
- ❌ Nombre vacío o con solo espacios
- ❌ Nombre ya en uso por otro usuario
- ✅ Nombre único y válido

---

### ✅ 3. Método `send-msg` (Mensaje Privado)

**Descripción:** Envía mensajes privados a un usuario específico del chat sin que otros usuarios puedan verlo.

**Ubicación:** Clase `Server.java` - Método `sendPrivateMessage(String fromUser, String toUser, String message)`

**Implementación:**
```java
static boolean sendPrivateMessage(String fromUser, String toUser, String message) {
    ClientHandler target = clients.get(toUser);
    if (target == null) return false;
    target.send("[Private][" + fromUser + "]: " + message);
    return true;
}
```

**Comando para el cliente:**
```
/msg USUARIO MENSAJE
```

**Ejemplos de uso:**
```
/msg Maria Hola, ¿cómo estás?
/msg Admin Necesito ayuda con el proyecto
/msg Pedro ¿Viste el partido de ayer?
```

**Funcionamiento:**
1. El cliente escribe `/msg` seguido del nombre del destinatario y el mensaje
2. El servidor busca al usuario destinatario en el mapa de clientes
3. Si existe, envía el mensaje exclusivamente a ese usuario
4. El mensaje se marca como `[Private]` para identificarlo
5. Si el usuario no existe, notifica al remitente del error
6. El mensaje se registra en el log del servidor

**Formato del mensaje recibido:**
```
[Private][Juan]: Hola, ¿cómo estás?
```

**Validaciones:**
- ❌ Usuario destinatario no encontrado → Error
- ❌ Mensaje vacío → No se envía
- ✅ Usuario existe y mensaje válido → Se envía

---

### ✅ 4. Método `global-msg` (Mensaje Global)

**Descripción:** Envía mensajes visibles para todos los usuarios conectados al chat.

**Ubicación:** Clase `Server.java` - Método `sendGlobalMessage(String fromUser, String message)`

**Implementación:**
```java
static void sendGlobalMessage(String fromUser, String message) {
    String payload = (fromUser == null)
            ? "[Server]: " + message
            : "[" + fromUser + "]: " + message;

    for (ClientHandler ch : clients.values()) {
        ch.send(payload);
    }
}
```

**Comandos para el cliente:**

**Opción 1 - Comando explícito:**
```
/all MENSAJE
```

**Opción 2 - Mensaje directo (comportamiento por defecto):**
```
MENSAJE
```

**Ejemplos de uso:**
```
/all Hola a todos
Hola a todos
¿Alguien sabe programar en Java?
/all ¿Qué opinan del nuevo proyecto?
```

**Funcionamiento:**
1. El cliente escribe un mensaje (con o sin `/all`)
2. El servidor identifica que es un mensaje global
3. Itera sobre todos los clientes conectados en el `ConcurrentHashMap`
4. Envía el mensaje a cada cliente activo
5. El mensaje se muestra con el formato `[Usuario]: mensaje`
6. Se registra en el log del servidor con timestamp

**Formato del mensaje recibido:**
```
[Juan]: Hola a todos
[Server]: Servidor en mantenimiento en 5 minutos
```

**Características especiales:**
- Los mensajes del servidor se identifican con `[Server]:`
- Los mensajes de usuarios se identifican con `[NombreUsuario]:`
- Todos los usuarios conectados reciben el mensaje simultáneamente
- Thread-safe mediante `ConcurrentHashMap`

---

## ⭐ FUNCIONALIDAD EXTRA: Comando `/kick` (Punto Extra)

### 🚫 Eliminación Administrativa de Usuarios

**Descripción:** Permite al administrador del servidor eliminar usuarios remotamente desde la consola del servidor.

**Ubicación:** Clase `Server.java` - Método `kickUser(String username)`

**Implementación:**
```java
static void kickUser(String username) {
    ClientHandler target = clients.get(username);
    if (target != null) {
        target.send("[Server]: Has sido eliminado del chat por el administrador.");
        target.forceDisconnect();
        clients.remove(username);
        sendGlobalMessage(null, username + " ha sido eliminado del chat por el administrador.");
        logMessage("Server", "Usuario " + username + " eliminado por el administrador.");
    } else {
        System.out.println("Usuario '" + username + "' no encontrado.");
        logMessage("Server", "Intento de eliminar usuario inexistente: " + username);
    }
}
```

**Método auxiliar en `ClientHandler`:**
```java
void forceDisconnect() {
    try {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    } catch (IOException ignored) {}
}
```

### 📝 Uso del Comando `/kick`

**Desde la consola del servidor:**
```
/kick NOMBRE_USUARIO
```

**Ejemplos:**
```
/kick Juan
/kick UsuarioMolesto
/kick Spammer123
```

### 🔄 Proceso de Eliminación

**Paso 1:** El administrador escribe el comando en la consola del servidor
```
/kick Juan
```

**Paso 2:** El servidor ejecuta las siguientes acciones:

1. **Busca al usuario** en el mapa de clientes conectados
2. **Notifica al usuario** que será eliminado:
   ```
   [Server]: Has sido eliminado del chat por el administrador.
   ```
3. **Cierra forzosamente** el socket del usuario (`forceDisconnect()`)
4. **Elimina al usuario** del `ConcurrentHashMap` de clientes
5. **Notifica a todos** los usuarios conectados:
   ```
   [Server]: Juan ha sido eliminado del chat por el administrador.
   ```
6. **Registra la acción** en el log del servidor:
   ```
   2025-10-29 14:30:45 [Server]: Usuario Juan eliminado por el administrador.
   ```

**Paso 3:** El cliente eliminado:
- Recibe la notificación
- Su conexión se cierra automáticamente
- Sale del programa

### 🎯 Características de Seguridad

✅ **Validación de usuarios:** Verifica que el usuario exista antes de intentar eliminarlo

✅ **Notificación previa:** El usuario recibe un mensaje antes de ser desconectado

✅ **Transparencia total:** Todos los usuarios son notificados de la acción administrativa

✅ **Registro completo:** Todas las acciones se registran en el log con timestamp

✅ **Manejo de errores:** Si el usuario no existe, se notifica al administrador sin afectar el sistema

### 📊 Mensajes de Ejemplo

**En el servidor:**
```
[NUEVO] Comandos disponibles: 
/kick USUARIO	Eliminar un usuario del chat

2025-10-29 14:30:45 [Server]: Usuario Juan eliminado por el administrador.
```

**En el cliente eliminado (Juan):**
```
[Server]: Has sido eliminado del chat por el administrador.
[Conexión cerrada]
```

**En todos los demás clientes:**
```
[Server]: Juan ha sido eliminado del chat por el administrador.
```

### 💡 Casos de Uso

1. **Moderación:** Eliminar usuarios que violan las reglas del chat
2. **Mantenimiento:** Remover conexiones inactivas o problemáticas
3. **Seguridad:** Expulsar usuarios sospechosos o maliciosos
4. **Gestión:** Liberar recursos del servidor eliminando usuarios específicos

---

## 🛠️ Instalación y Ejecución

### Requisitos Previos
- **Java Development Kit (JDK) 11 o superior**
- **Terminal/CMD** con acceso a comandos Java

### Compilación

**1. Compilar el servidor:**
```bash
javac Server.java
```

**2. Compilar el cliente:**
```bash
javac Client.java
```

### Ejecución

**1. Iniciar el servidor:**
```bash
java Server
```

**Salida esperada:**
```
Servidor de chat iniciado en puerto 8080
¡Servidor creado exitosamente!
[NUEVO] Comandos disponibles: 
/kick USUARIO	Eliminar un usuario del chat
```

**2. Conectar clientes:**

**Cliente con conexión local:**
```bash
java Client
```

**Cliente con nombre predefinido:**
```bash
java Client Maria
```

**Cliente con IP y puerto personalizados:**
```bash
java Client Pedro 192.168.1.100 8080
```

---

## 📚 Comandos Disponibles

### Comandos del Cliente

| Comando | Descripción | Ejemplo |
|---------|-------------|---------|
| `/help` | Muestra lista de comandos | `/help` |
| `/rename NOMBRE` | Cambia tu nombre de usuario | `/rename JuanPerez` |
| `/msg USUARIO MENSAJE` | Envía mensaje privado | `/msg Maria Hola` |
| `/all MENSAJE` | Envía mensaje global | `/all Hola a todos` |
| `MENSAJE` | Envía mensaje global (por defecto) | `Hola` |
| `/quit` | Salir del chat | `/quit` |

### Comandos del Servidor (Administrador)

| Comando | Descripción | Ejemplo |
|---------|-------------|---------|
| **`/kick USUARIO`** | **Elimina un usuario del chat** | **`/kick Juan`** |
| `MENSAJE` | Envía mensaje global como servidor | `Mantenimiento en 5 min` |

---

## 🏗️ Arquitectura Técnica

### Componentes Principales

#### 1. **Servidor (`Server.java`)**
- **Puerto por defecto:** 8080
- **Concurrencia:** `ConcurrentHashMap<String, ClientHandler>` para gestión thread-safe
- **Hilos:**
  - Hilo principal: Acepta conexiones entrantes
  - Hilo por cliente: Maneja comunicación individual (`ClientHandler`)
  - Hilo de consola: Procesa comandos administrativos
- **Logging:** Timestamp automático en formato `yyyy-MM-dd HH:mm:ss`

#### 2. **Cliente (`Client.java`)**
- **Conexión:** Socket TCP hacia el servidor
- **Hilos:**
  - Hilo principal: Envío de mensajes al servidor
  - Hilo daemon: Recepción de mensajes del servidor
- **Manejo de desconexión:** Cierre limpio de recursos

#### 3. **Manejador de Clientes (`ClientHandler`)**
- **Responsabilidades:**
  - Autenticación de usuarios
  - Procesamiento de comandos
  - Envío de mensajes
  - Gestión de desconexiones

### Flujo de Comunicación

```
┌─────────────┐                  ┌─────────────┐                  ┌─────────────┐
│  Cliente A  │                  │   Servidor  │                  │  Cliente B  │
└──────┬──────┘                  └──────┬──────┘                  └──────┬──────┘
       │                                │                                │
       │──── Socket TCP (8080) ────────>│                                │
       │                                │                                │
       │<─── "Ingresa nombre" ──────────│                                │
       │                                │                                │
       │──── "Juan" ───────────────────>│                                │
       │                                │──── "Juan se unió" ──────────>│
       │<─── Confirmación ──────────────│                                │
       │                                │                                │
       │──── "Hola a todos" ───────────>│                                │
       │                                │──── "[Juan]: Hola" ──────────>│
       │                                │                                │
       │──── "/msg Maria Hola" ────────>│                                │
       │                                │──── "[Private][Juan]" ────────>│
       │                                │                                │
```

### Diagrama de Clases (Simplificado)

```
Server
├── ConcurrentMap<String, ClientHandler> clients
├── main()
├── startConnection()  ← MÉTODO REQUERIDO (2.5 puntos)
├── changeUserName()   ← MÉTODO REQUERIDO
├── sendPrivateMessage() ← MÉTODO REQUERIDO (send-msg)
├── sendGlobalMessage()  ← MÉTODO REQUERIDO (global-msg)
├── kickUser()         ← FUNCIONALIDAD EXTRA (punto extra)
└── ClientHandler
    ├── Socket socket
    ├── run()
    ├── send()
    └── forceDisconnect() ← NUEVO para /kick

Client
├── Socket socket
├── startConnection() ← MÉTODO REQUERIDO (2.5 puntos)
├── main()
└── closeQuietly()
```

---

## 🔒 Características de Seguridad

✅ **Thread-safe:** Uso de `ConcurrentHashMap` para evitar condiciones de carrera

✅ **Validación de nombres:** Nombres únicos y no vacíos

✅ **Manejo de excepciones:** Gestión robusta de `IOException` y `EOFException`

✅ **Cierre de recursos:** Try-with-resources y métodos `closeQuietly()`

✅ **Control administrativo:** Comando `/kick` para moderación

✅ **Notificaciones transparentes:** Todas las acciones administrativas son visibles

---

## 📊 Registro de Mensajes (Logging)

Todos los eventos del servidor se registran con el siguiente formato:

```
yyyy-MM-dd HH:mm:ss [Usuario/Server]: Mensaje
```

**Ejemplos:**
```
2025-10-29 14:25:30 [Server]: Juan se unió al chat.
2025-10-29 14:26:15 [Juan]: Hola a todos
2025-10-29 14:27:00 [Server]: Juan cambió su nombre a JuanPerez.
2025-10-29 14:30:45 [Server]: Usuario Juan eliminado por el administrador.
```

---

## 🧪 Casos de Prueba

### Prueba 1: Conexión Exitosa
```bash
# Terminal 1
java Server

# Terminal 2
java Client Juan
```
**Resultado esperado:** Juan conectado y notificación global

### Prueba 2: Cambio de Nombre
```bash
/rename JuanPerez
```
**Resultado esperado:** Confirmación y notificación a todos

### Prueba 3: Mensaje Privado
```bash
/msg Maria Hola, ¿cómo estás?
```
**Resultado esperado:** Solo Maria recibe el mensaje

### Prueba 4: Mensaje Global
```bash
Hola a todos
```
**Resultado esperado:** Todos los usuarios reciben el mensaje

### Prueba 5: Eliminación Administrativa
```bash
# En consola del servidor
/kick Juan
```
**Resultado esperado:** 
- Juan desconectado
- Todos notificados
- Log actualizado

---

## 🐛 Solución de Problemas

### Error: "Connection refused"
**Causa:** Servidor no iniciado o puerto incorrecto  
**Solución:** Verificar que el servidor esté corriendo en el puerto 8080

### Error: "Address already in use"
**Causa:** Puerto 8080 ya está en uso  
**Solución:** Cerrar la aplicación que usa el puerto o cambiar el puerto en el código

### Error: Usuario no encontrado al usar `/msg`
**Causa:** Nombre de usuario incorrecto o usuario desconectado  
**Solución:** Verificar que el usuario esté conectado y el nombre sea correcto

### Error: No se puede cambiar nombre con `/rename`
**Causa:** Nombre ya en uso o vacío  
**Solución:** Elegir un nombre único y no vacío

---

## 📈 Mejoras Futuras (Opcional)

- 🔐 Autenticación con contraseñas
- 💾 Persistencia de mensajes en base de datos
- 🔔 Notificaciones de usuarios en línea
- 📁 Transferencia de archivos
- 🎨 Interfaz gráfica (GUI) con JavaFX
- 🌐 Soporte para múltiples salas de chat
- 📊 Estadísticas de uso y análisis
- 🔒 Cifrado de mensajes (TLS/SSL)

---

## 👨‍💻 Información del Proyecto
### Métodos Implementados (Requisitos)

1. ✅ **`start-connection`** - Conexión IP:Puerto (2.5 puntos)
2. ✅ **`change-userName`** - Cambio de nombre dinámico
3. ✅ **`send-msg`** - Mensajes privados
4. ✅ **`global-msg`** - Mensajes globales

### Funcionalidad Extra

5. ⭐ **`/kick`** - Eliminación administrativa de usuarios **(PUNTO EXTRA)**

---

**¡Disfruta del chat! 💬**# DiscordLocalServer
