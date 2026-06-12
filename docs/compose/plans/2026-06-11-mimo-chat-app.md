# MiMo Chat App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a mobile chat app that integrates MiMo-Code API with character presets, multimodal input, and voice/video call support.

**Architecture:** Fork Happy's React Native/Expo app, strip Claude/Codex integration, replace with direct MiMo-Code API calls. Add character system and voice/video features.

**Tech Stack:** React Native, Expo SDK 55, TypeScript, Unistyles, expo-audio, LiveKit (WebRTC), AsyncStorage, MMKV

---

## Phase 1: Project Setup & Cleanup

### Task 1: Initialize Project Structure

**Covers:** [S1]

**Files:**
- Create: `mimo-code-mobile/app/package.json`
- Create: `mimo-code-mobile/app/tsconfig.json`
- Create: `mimo-code-mobile/app/app.json`

- [ ] **Step 1: Copy Happy app as base**

```bash
cp -r mimo-code-mobile/happy-source/packages/happy-app/* mimo-code-mobile/app/
```

- [ ] **Step 2: Update package.json name and remove unnecessary dependencies**

Edit `mimo-code-mobile/app/package.json`:
```json
{
  "name": "mimo-chat",
  "version": "1.0.0",
  "main": "index.ts",
  "scripts": {
    "start": "expo start",
    "android": "expo run:android",
    "typecheck": "tsc --noEmit"
  }
}
```

- [ ] **Step 3: Commit**

```bash
cd mimo-code-mobile/app && git init && git add -A && git commit -m "chore: initialize project from happy app base"
```

---

### Task 2: Remove Claude/Codex Integration

**Covers:** [S1]

**Files:**
- Delete: `mimo-code-mobile/app/sources/sync/` (except storage.ts, settings.ts)
- Delete: `mimo-code-mobile/app/sources/auth/`
- Delete: `mimo-code-mobile/app/sources/encryption/`
- Modify: `mimo-code-mobile/app/sources/app/_layout.tsx`

- [ ] **Step 1: Remove sync directory except core files**

```bash
cd mimo-code-mobile/app/sources
rm -rf sync/api*.ts sync/ops.ts sync/prompt/ sync/git-parsers/
mv sync/storage.ts .
mv sync/settings.ts .
rm -rf sync/
mkdir -p sync
mv storage.ts sync/
mv settings.ts sync/
```

- [ ] **Step 2: Remove auth directory**

```bash
rm -rf auth/
```

- [ ] **Step 3: Remove encryption directory**

```bash
rm -rf encryption/
```

- [ ] **Step 4: Simplify _layout.tsx**

Replace `mimo-code-mobile/app/sources/app/_layout.tsx` with simplified version (remove AuthProvider, encryption, sync restore):

```typescript
import '../theme.css';
import * as React from 'react';
import * as SplashScreen from 'expo-splash-screen';
import * as Fonts from 'expo-font';
import { View, Platform } from 'react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { KeyboardProvider } from 'react-native-keyboard-controller';
import { ThemeProvider, DarkTheme, DefaultTheme } from '@react-navigation/native';
import { useUnistyles } from 'react-native-unistyles';
import { StatusBarProvider } from '@/components/StatusBarProvider';
import { ModalProvider } from '@/modal';

SplashScreen.preventAutoHideAsync();

function loadFonts() {
  return Fonts.loadAsync({
    'IBMPlexSans-Regular': require('@/assets/fonts/IBMPlexSans-Regular.ttf'),
    'IBMPlexSans-SemiBold': require('@/assets/fonts/IBMPlexSans-SemiBold.ttf'),
  });
}

export default function RootLayout() {
  const { theme } = useUnistyles();
  const [loaded, setLoaded] = React.useState(false);

  React.useEffect(() => {
    loadFonts().then(() => {
      setLoaded(true);
      SplashScreen.hideAsync();
    });
  }, []);

  if (!loaded) return null;

  const navTheme = theme.dark
    ? { ...DarkTheme, colors: { ...DarkTheme.colors, background: theme.colors.groupped.background } }
    : { ...DefaultTheme, colors: { ...DefaultTheme.colors, background: theme.colors.groupped.background } };

  return (
    <SafeAreaProvider initialMetrics={initialWindowMetrics}>
      <KeyboardProvider>
        <GestureHandlerRootView style={{ flex: 1 }}>
          <ThemeProvider value={navTheme}>
            <StatusBarProvider />
            <ModalProvider>
              {/* Navigation will be added here */}
            </ModalProvider>
          </ThemeProvider>
        </GestureHandlerRootView>
      </KeyboardProvider>
    </SafeAreaProvider>
  );
}
```

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: remove Claude/Codex integration, simplify layout"
```

---

## Phase 2: MiMo API Client

### Task 3: Create MiMo API Client

**Covers:** [S2]

**Files:**
- Create: `mimo-code-mobile/app/sources/mimo/MiMoClient.ts`
- Create: `mimo-code-mobile/app/sources/mimo/types.ts`

- [ ] **Step 1: Create types.ts**

```typescript
// mimo-code-mobile/app/sources/mimo/types.ts

export interface MiMoConfig {
  baseUrl: string;
  apiKey: string;
}

export interface Session {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
}

export interface Message {
  id: string;
  sessionId: string;
  role: 'user' | 'assistant';
  content: string;
  parts: Part[];
  createdAt: number;
}

export interface Part {
  type: 'text' | 'file' | 'image';
  content: string;
  mimeType?: string;
}

export interface CreateSessionParams {
  title?: string;
}

export interface SendMessageParams {
  sessionId: string;
  content: string;
  parts?: Part[];
  system?: string;
}
```

- [ ] **Step 2: Create MiMoClient.ts**

```typescript
// mimo-code-mobile/app/sources/mimo/MiMoClient.ts
import { MiMoConfig, Session, Message, CreateSessionParams, SendMessageParams } from './types';

export class MiMoClient {
  private config: MiMoConfig;

  constructor(config: MiMoConfig) {
    this.config = config;
  }

  private async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const url = `${this.config.baseUrl}${path}`;
    const response = await fetch(url, {
      ...options,
      headers: {
        'Authorization': `Bearer ${this.config.apiKey}`,
        'Content-Type': 'application/json',
        ...options.headers,
      },
    });

    if (!response.ok) {
      throw new Error(`MiMo API error: ${response.status}`);
    }

    return response.json();
  }

  async createSession(params?: CreateSessionParams): Promise<Session> {
    return this.request<Session>('/session', {
      method: 'POST',
      body: JSON.stringify(params),
    });
  }

  async getSession(sessionId: string): Promise<Session> {
    return this.request<Session>(`/session/${sessionId}`);
  }

  async listSessions(): Promise<Session[]> {
    return this.request<Session[]>('/session');
  }

  async deleteSession(sessionId: string): Promise<boolean> {
    return this.request<boolean>(`/session/${sessionId}`, {
      method: 'DELETE',
    });
  }

  async getMessages(sessionId: string): Promise<Message[]> {
    return this.request<Message[]>(`/session/${sessionId}/messages`);
  }

  async sendMessage(params: SendMessageParams): Promise<Message> {
    const parts = params.parts || [{ type: 'text', content: params.content }];
    
    return this.request<Message>(`/session/${params.sessionId}/prompt`, {
      method: 'POST',
      body: JSON.stringify({
        parts,
        system: params.system,
      }),
    });
  }

  async sendImage(sessionId: string, imageBase64: string, mimeType: string, text?: string): Promise<Message> {
    const parts = [
      { type: 'file', content: imageBase64, mimeType },
    ];
    if (text) {
      parts.unshift({ type: 'text', content: text });
    }
    
    return this.request<Message>(`/session/${sessionId}/prompt`, {
      method: 'POST',
      body: JSON.stringify({ parts }),
    });
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: add MiMo API client with session and message support"
```

---

### Task 4: Create MiMo Store

**Covers:** [S2]

**Files:**
- Create: `mimo-code-mobile/app/sources/mimo/store.ts`

- [ ] **Step 1: Create store.ts**

```typescript
// mimo-code-mobile/app/sources/mimo/store.ts
import { create } from 'zustand';
import { MiMoClient } from './MiMoClient';
import { Session, Message, MiMoConfig } from './types';
import AsyncStorage from '@react-native-async-storage/async-storage';

interface MiMoStore {
  config: MiMoConfig | null;
  client: MiMoClient | null;
  sessions: Session[];
  currentSession: Session | null;
  messages: Message[];
  isLoading: boolean;
  
  setConfig: (config: MiMoConfig) => Promise<void>;
  loadConfig: () => Promise<void>;
  createSession: (title?: string) => Promise<Session>;
  selectSession: (session: Session) => Promise<void>;
  loadSessions: () => Promise<void>;
  sendMessage: (content: string, system?: string) => Promise<Message>;
  sendImage: (imageBase64: string, mimeType: string, text?: string) => Promise<Message>;
}

export const useMiMoStore = create<MiMoStore>((set, get) => ({
  config: null,
  client: null,
  sessions: [],
  currentSession: null,
  messages: [],
  isLoading: false,

  setConfig: async (config: MiMoConfig) => {
    const client = new MiMoClient(config);
    await AsyncStorage.setItem('mimo_config', JSON.stringify(config));
    set({ config, client });
  },

  loadConfig: async () => {
    const stored = await AsyncStorage.getItem('mimo_config');
    if (stored) {
      const config = JSON.parse(stored) as MiMoConfig;
      const client = new MiMoClient(config);
      set({ config, client });
    }
  },

  createSession: async (title?: string) => {
    const { client } = get();
    if (!client) throw new Error('Client not initialized');
    
    const session = await client.createSession({ title });
    set((state) => ({ sessions: [session, ...state.sessions] }));
    return session;
  },

  selectSession: async (session: Session) => {
    const { client } = get();
    if (!client) throw new Error('Client not initialized');
    
    const messages = await client.getMessages(session.id);
    set({ currentSession: session, messages });
  },

  loadSessions: async () => {
    const { client } = get();
    if (!client) return;
    
    const sessions = await client.listSessions();
    set({ sessions });
  },

  sendMessage: async (content: string, system?: string) => {
    const { client, currentSession } = get();
    if (!client || !currentSession) throw new Error('No session selected');
    
    set({ isLoading: true });
    try {
      const message = await client.sendMessage({
        sessionId: currentSession.id,
        content,
        system,
      });
      set((state) => ({ messages: [...state.messages, message] }));
      return message;
    } finally {
      set({ isLoading: false });
    }
  },

  sendImage: async (imageBase64: string, mimeType: string, text?: string) => {
    const { client, currentSession } = get();
    if (!client || !currentSession) throw new Error('No session selected');
    
    set({ isLoading: true });
    try {
      const message = await client.sendImage(currentSession.id, imageBase64, mimeType, text);
      set((state) => ({ messages: [...state.messages, message] }));
      return message;
    } finally {
      set({ isLoading: false });
    }
  },
}));
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "feat: add MiMo store with session and message management"
```

---

## Phase 3: Character System

### Task 5: Create Character Types and Presets

**Covers:** [S3]

**Files:**
- Create: `mimo-code-mobile/app/sources/characters/types.ts`
- Create: `mimo-code-mobile/app/sources/characters/presets.ts`
- Create: `mimo-code-mobile/app/sources/characters/store.ts`

- [ ] **Step 1: Create types.ts**

```typescript
// mimo-code-mobile/app/sources/characters/types.ts

export interface Character {
  id: string;
  name: string;
  avatar: string;
  voiceId: string;
  systemPrompt: string;
  description: string;
}
```

- [ ] **Step 2: Create presets.ts**

```typescript
// mimo-code-mobile/app/sources/characters/presets.ts
import { Character } from './types';

export const CHARACTER_PRESETS: Character[] = [
  {
    id: 'assistant',
    name: '小助手',
    avatar: '🤖',
    voiceId: 'default',
    systemPrompt: '你是一个友好的AI助手，善于解答各种问题。',
    description: '通用助手，乐于助人',
  },
  {
    id: 'programmer',
    name: '程序员',
    avatar: '💻',
    voiceId: 'default',
    systemPrompt: '你是一个经验丰富的程序员，精通各种编程语言和最佳实践。',
    description: '编程专家，代码助手',
  },
  {
    id: 'writer',
    name: '作家',
    avatar: '✍️',
    voiceId: 'default',
    systemPrompt: '你是一个富有创意的作家，善于用优美的文字表达思想。',
    description: '创意写作，文字大师',
  },
  {
    id: 'teacher',
    name: '老师',
    avatar: '📚',
    voiceId: 'default',
    systemPrompt: '你是一个耐心的老师，善于用简单易懂的方式解释复杂概念。',
    description: '教育专家，知识传授',
  },
];
```

- [ ] **Step 3: Create store.ts**

```typescript
// mimo-code-mobile/app/sources/characters/store.ts
import { create } from 'zustand';
import { Character } from './types';
import { CHARACTER_PRESETS } from './presets';
import AsyncStorage from '@react-native-async-storage/async-storage';

interface CharacterStore {
  characters: Character[];
  selectedCharacter: Character;
  loadCharacters: () => Promise<void>;
  selectCharacter: (character: Character) => void;
}

export const useCharacterStore = create<CharacterStore>((set, get) => ({
  characters: CHARACTER_PRESETS,
  selectedCharacter: CHARACTER_PRESETS[0],

  loadCharacters: async () => {
    const stored = await AsyncStorage.getItem('selected_character');
    if (stored) {
      const character = JSON.parse(stored) as Character;
      set({ selectedCharacter: character });
    }
  },

  selectCharacter: async (character: Character) => {
    await AsyncStorage.setItem('selected_character', JSON.stringify(character));
    set({ selectedCharacter: character });
  },
}));
```

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: add character system with presets"
```

---

## Phase 4: Chat UI

### Task 6: Create Chat Screen

**Covers:** [S4]

**Files:**
- Create: `mimo-code-mobile/app/sources/app/(app)/chat.tsx`
- Create: `mimo-code-mobile/app/sources/components/ChatMessage.tsx`
- Create: `mimo-code-mobile/app/sources/components/ChatInput.tsx`

- [ ] **Step 1: Create ChatMessage.tsx**

```typescript
// mimo-code-mobile/app/sources/components/ChatMessage.tsx
import * as React from 'react';
import { View, Text } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { Message } from '@/mimo/types';

interface Props {
  message: Message;
  isUser: boolean;
}

export function ChatMessage({ message, isUser }: Props) {
  return (
    <View style={[styles.container, isUser ? styles.userContainer : styles.assistantContainer]}>
      <View style={[styles.bubble, isUser ? styles.userBubble : styles.assistantBubble]}>
        <Text style={[styles.text, isUser ? styles.userText : styles.assistantText]}>
          {message.content}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create((theme) => ({
  container: {
    marginVertical: 4,
    paddingHorizontal: 16,
  },
  userContainer: {
    alignItems: 'flex-end',
  },
  assistantContainer: {
    alignItems: 'flex-start',
  },
  bubble: {
    maxWidth: '80%',
    padding: 12,
    borderRadius: 16,
  },
  userBubble: {
    backgroundColor: theme.colors.primary,
    borderBottomRightRadius: 4,
  },
  assistantBubble: {
    backgroundColor: theme.colors.background,
    borderBottomLeftRadius: 4,
  },
  text: {
    fontSize: 16,
    lineHeight: 22,
  },
  userText: {
    color: '#ffffff',
  },
  assistantText: {
    color: theme.colors.text,
  },
}));
```

- [ ] **Step 2: Create ChatInput.tsx**

```typescript
// mimo-code-mobile/app/sources/components/ChatInput.tsx
import * as React from 'react';
import { View, TextInput, TouchableOpacity, Text } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import * as ImagePicker from 'expo-image-picker';
import * as FileSystem from 'expo-file-system';

interface Props {
  onSend: (text: string) => void;
  onSendImage: (base64: string, mimeType: string, text?: string) => void;
  isLoading: boolean;
}

export function ChatInput({ onSend, onSendImage, isLoading }: Props) {
  const [text, setText] = React.useState('');

  const handleSend = () => {
    if (text.trim() && !isLoading) {
      onSend(text.trim());
      setText('');
    }
  };

  const handlePickImage = async () => {
    const result = await ImagePicker.launchImageLibraryAsync({
      mediaTypes: ['images'],
      quality: 0.8,
    });

    if (!result.canceled && result.assets[0]) {
      const asset = result.assets[0];
      const base64 = await FileSystem.readAsStringAsync(asset.uri, {
        encoding: FileSystem.EncodingType.Base64,
      });
      onSendImage(base64, asset.mimeType || 'image/jpeg', text.trim() || undefined);
      setText('');
    }
  };

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.imageButton} onPress={handlePickImage}>
        <Text style={styles.imageButtonText}>📷</Text>
      </TouchableOpacity>
      <TextInput
        style={styles.input}
        value={text}
        onChangeText={setText}
        placeholder="输入消息..."
        multiline
        maxLength={4000}
      />
      <TouchableOpacity
        style={[styles.sendButton, (!text.trim() || isLoading) && styles.sendButtonDisabled]}
        onPress={handleSend}
        disabled={!text.trim() || isLoading}
      >
        <Text style={styles.sendButtonText}>发送</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create((theme) => ({
  container: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    padding: 12,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
    backgroundColor: theme.colors.background,
  },
  imageButton: {
    padding: 8,
    marginRight: 8,
  },
  imageButtonText: {
    fontSize: 24,
  },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: theme.colors.border,
    borderRadius: 20,
    paddingHorizontal: 16,
    paddingVertical: 10,
    fontSize: 16,
    maxHeight: 100,
    color: theme.colors.text,
  },
  sendButton: {
    marginLeft: 8,
    paddingHorizontal: 16,
    paddingVertical: 10,
    backgroundColor: theme.colors.primary,
    borderRadius: 20,
  },
  sendButtonDisabled: {
    opacity: 0.5,
  },
  sendButtonText: {
    color: '#ffffff',
    fontWeight: '600',
  },
}));
```

- [ ] **Step 3: Create chat.tsx screen**

```typescript
// mimo-code-mobile/app/sources/app/(app)/chat.tsx
import * as React from 'react';
import { View, FlatList, ActivityIndicator } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { useMiMoStore } from '@/mimo/store';
import { useCharacterStore } from '@/characters/store';
import { ChatMessage } from '@/components/ChatMessage';
import { ChatInput } from '@/components/ChatInput';

export default function ChatScreen() {
  const { messages, isLoading, sendMessage, sendImage, currentSession } = useMiMoStore();
  const { selectedCharacter } = useCharacterStore();
  const flatListRef = React.useRef<FlatList>(null);

  React.useEffect(() => {
    if (messages.length > 0) {
      flatListRef.current?.scrollToEnd({ animated: true });
    }
  }, [messages]);

  const handleSend = async (text: string) => {
    if (!currentSession) {
      // Create session if none exists
      await useMiMoStore.getState().createSession('新对话');
    }
    await sendMessage(text, selectedCharacter.systemPrompt);
  };

  const handleSendImage = async (base64: string, mimeType: string, text?: string) => {
    if (!currentSession) {
      await useMiMoStore.getState().createSession('图片对话');
    }
    await sendImage(base64, mimeType, text);
  };

  return (
    <View style={styles.container}>
      <FlatList
        ref={flatListRef}
        data={messages}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => (
          <ChatMessage
            message={item}
            isUser={item.role === 'user'}
          />
        )}
        contentContainerStyle={styles.messageList}
      />
      {isLoading && (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="small" color="#007AFF" />
        </View>
      )}
      <ChatInput
        onSend={handleSend}
        onSendImage={handleSendImage}
        isLoading={isLoading}
      />
    </View>
  );
}

const styles = StyleSheet.create((theme) => ({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  messageList: {
    paddingVertical: 16,
  },
  loadingContainer: {
    padding: 8,
    alignItems: 'center',
  },
}));
```

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: add chat screen with message display and input"
```

---

### Task 7: Add Character Selection UI

**Covers:** [S3]

**Files:**
- Create: `mimo-code-mobile/app/sources/components/CharacterPicker.tsx`
- Modify: `mimo-code-mobile/app/sources/app/(app)/chat.tsx`

- [ ] **Step 1: Create CharacterPicker.tsx**

```typescript
// mimo-code-mobile/app/sources/components/CharacterPicker.tsx
import * as React from 'react';
import { View, Text, TouchableOpacity, FlatList } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { useCharacterStore } from '@/characters/store';
import { Character } from '@/characters/types';

export function CharacterPicker() {
  const { characters, selectedCharacter, selectCharacter } = useCharacterStore();
  const [isOpen, setIsOpen] = React.useState(false);

  return (
    <View style={styles.container}>
      <TouchableOpacity style={styles.trigger} onPress={() => setIsOpen(!isOpen)}>
        <Text style={styles.avatar}>{selectedCharacter.avatar}</Text>
        <Text style={styles.name}>{selectedCharacter.name}</Text>
        <Text style={styles.arrow}>{isOpen ? '▲' : '▼'}</Text>
      </TouchableOpacity>
      
      {isOpen && (
        <View style={styles.dropdown}>
          {characters.map((character) => (
            <TouchableOpacity
              key={character.id}
              style={[
                styles.option,
                character.id === selectedCharacter.id && styles.optionSelected,
              ]}
              onPress={() => {
                selectCharacter(character);
                setIsOpen(false);
              }}
            >
              <Text style={styles.optionAvatar}>{character.avatar}</Text>
              <View style={styles.optionContent}>
                <Text style={styles.optionName}>{character.name}</Text>
                <Text style={styles.optionDescription}>{character.description}</Text>
              </View>
            </TouchableOpacity>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create((theme) => ({
  container: {
    zIndex: 100,
  },
  trigger: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    backgroundColor: theme.colors.background,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  avatar: {
    fontSize: 24,
    marginRight: 8,
  },
  name: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
    flex: 1,
  },
  arrow: {
    fontSize: 12,
    color: theme.colors.textSecondary,
  },
  dropdown: {
    position: 'absolute',
    top: '100%',
    left: 0,
    right: 0,
    backgroundColor: theme.colors.background,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 4,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  optionSelected: {
    backgroundColor: theme.colors.primary + '20',
  },
  optionAvatar: {
    fontSize: 24,
    marginRight: 12,
  },
  optionContent: {
    flex: 1,
  },
  optionName: {
    fontSize: 16,
    fontWeight: '600',
    color: theme.colors.text,
  },
  optionDescription: {
    fontSize: 14,
    color: theme.colors.textSecondary,
    marginTop: 2,
  },
}));
```

- [ ] **Step 2: Add CharacterPicker to chat.tsx**

Edit `mimo-code-mobile/app/sources/app/(app)/chat.tsx` to include CharacterPicker at the top.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: add character picker dropdown to chat screen"
```

---

## Phase 5: Settings

### Task 8: Create Settings Screen

**Covers:** [S2]

**Files:**
- Create: `mimo-code-mobile/app/sources/app/(app)/settings.tsx`

- [ ] **Step 1: Create settings.tsx**

```typescript
// mimo-code-mobile/app/sources/app/(app)/settings.tsx
import * as React from 'react';
import { View, Text, TextInput, TouchableOpacity, Alert } from 'react-native';
import { StyleSheet } from 'react-native-unistyles';
import { useMiMoStore } from '@/mimo/store';
import AsyncStorage from '@react-native-async-storage/async-storage';

export default function SettingsScreen() {
  const { config, setConfig } = useMiMoStore();
  const [baseUrl, setBaseUrl] = React.useState(config?.baseUrl || '');
  const [apiKey, setApiKey] = React.useState(config?.apiKey || '');

  const handleSave = async () => {
    if (!baseUrl.trim() || !apiKey.trim()) {
      Alert.alert('错误', '请填写所有字段');
      return;
    }
    await setConfig({ baseUrl: baseUrl.trim(), apiKey: apiKey.trim() });
    Alert.alert('成功', '配置已保存');
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>MiMo API 设置</Text>
      
      <View style={styles.field}>
        <Text style={styles.label}>API 地址</Text>
        <TextInput
          style={styles.input}
          value={baseUrl}
          onChangeText={setBaseUrl}
          placeholder="https://api.mimo.xiaomi.com"
          autoCapitalize="none"
          autoCorrect={false}
        />
      </View>

      <View style={styles.field}>
        <Text style={styles.label}>API Key</Text>
        <TextInput
          style={styles.input}
          value={apiKey}
          onChangeText={setApiKey}
          placeholder="输入你的 API Key"
          secureTextEntry
          autoCapitalize="none"
          autoCorrect={false}
        />
      </View>

      <TouchableOpacity style={styles.saveButton} onPress={handleSave}>
        <Text style={styles.saveButtonText}>保存</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create((theme) => ({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: theme.colors.background,
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
    color: theme.colors.text,
    marginBottom: 24,
  },
  field: {
    marginBottom: 16,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
    color: theme.colors.text,
    marginBottom: 8,
  },
  input: {
    borderWidth: 1,
    borderColor: theme.colors.border,
    borderRadius: 8,
    padding: 12,
    fontSize: 16,
    color: theme.colors.text,
  },
  saveButton: {
    backgroundColor: theme.colors.primary,
    borderRadius: 8,
    padding: 16,
    alignItems: 'center',
    marginTop: 16,
  },
  saveButtonText: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '600',
  },
}));
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "feat: add settings screen for MiMo API configuration"
```

---

## Phase 6: Voice Features

### Task 9: Add Voice Input (ASR)

**Covers:** [S4]

**Files:**
- Create: `mimo-code-mobile/app/sources/voice/ASRService.ts`
- Modify: `mimo-code-mobile/app/sources/components/ChatInput.tsx`

- [ ] **Step 1: Create ASRService.ts**

```typescript
// mimo-code-mobile/app/sources/voice/ASRService.ts
import { Audio } from 'expo-audio';

export class ASRService {
  private recording: Audio.Recording | null = null;

  async startRecording(): Promise<void> {
    await Audio.requestPermissionsAsync();
    await Audio.setAudioModeAsync({
      allowsRecordingIOS: true,
      playsInSilentModeIOS: true,
    });

    const { recording } = await Audio.Recording.createAsync(
      Audio.RecordingOptionsPresets.HIGH_QUALITY
    );
    this.recording = recording;
  }

  async stopRecording(): Promise<string> {
    if (!this.recording) {
      throw new Error('No recording in progress');
    }

    await this.recording.stopAndUnloadAsync();
    await Audio.setAudioModeAsync({ allowsRecordingIOS: false });

    const uri = this.recording.getURI();
    this.recording = null;

    // TODO: Send to MiMo ASR API for transcription
    // For now, return the URI
    return uri || '';
  }

  async transcribe(uri: string): Promise<string> {
    // TODO: Implement MiMo ASR API call
    // This would send the audio file and return transcribed text
    return '';
  }
}
```

- [ ] **Step 2: Add voice button to ChatInput.tsx**

Edit `mimo-code-mobile/app/sources/components/ChatInput.tsx` to add a microphone button.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "feat: add voice input (ASR) service"
```

---

### Task 10: Add Voice Output (TTS)

**Covers:** [S4]

**Files:**
- Create: `mimo-code-mobile/app/sources/voice/TTSService.ts`

- [ ] **Step 1: Create TTSService.ts**

```typescript
// mimo-code-mobile/app/sources/voice/TTSService.ts
import { Audio } from 'expo-audio';

export class TTSService {
  private sound: Audio.Sound | null = null;

  async speak(text: string, voiceId?: string): Promise<void> {
    // TODO: Implement MiMo TTS API call
    // This would generate audio from text and play it
    
    // Placeholder: Use device TTS
    console.log('TTS:', text);
  }

  async stop(): Promise<void> {
    if (this.sound) {
      await this.sound.stopAsync();
      await this.sound.unloadAsync();
      this.sound = null;
    }
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "feat: add voice output (TTS) service"
```

---

## Phase 7: Video Call (Future)

### Task 11: Add Video Call Placeholder

**Covers:** [S5]

**Files:**
- Create: `mimo-code-mobile/app/sources/voice/VideoCallService.ts`

- [ ] **Step 1: Create VideoCallService.ts**

```typescript
// mimo-code-mobile/app/sources/voice/VideoCallService.ts

export class VideoCallService {
  async startCall(sessionId: string): Promise<void> {
    // TODO: Implement WebRTC video call with MiMo
    console.log('Starting video call for session:', sessionId);
  }

  async endCall(): Promise<void> {
    console.log('Ending video call');
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add -A && git commit -m "feat: add video call service placeholder"
```

---

## Summary

This plan covers:
1. **Phase 1**: Project setup and cleanup (remove Claude/Codex)
2. **Phase 2**: MiMo API client integration
3. **Phase 3**: Character system with presets
4. **Phase 4**: Chat UI with messages and input
5. **Phase 5**: Settings screen for API configuration
6. **Phase 6**: Voice input/output services
7. **Phase 7**: Video call placeholder

**Estimated Tasks:** 11 tasks
**Estimated Time:** 2-3 hours for core functionality