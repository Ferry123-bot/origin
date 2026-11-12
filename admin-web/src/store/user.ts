import { defineStore } from 'pinia'
import { ref } from 'vue'

export const ADMIN_TOKEN_KEY = 'yszx_admin_token'
export const ADMIN_REFRESH_KEY = 'yszx_admin_refresh'
const NAME_KEY = 'yszx_admin_username'
const ROLE_KEY = 'yszx_admin_role'
const PERM_KEY = 'yszx_admin_permissions'

const SESSION_KEYS = [ADMIN_TOKEN_KEY, ADMIN_REFRESH_KEY, NAME_KEY, ROLE_KEY, PERM_KEY]

function readItem(key: string) {
  return sessionStorage.getItem(key) ?? localStorage.getItem(key) ?? ''
}

function rememberEnabled() {
  return !sessionStorage.getItem(ADMIN_TOKEN_KEY)
}

function writeItem(key: string, value: string) {
  const primary = rememberEnabled() ? localStorage : sessionStorage
  const other = rememberEnabled() ? sessionStorage : localStorage
  primary.setItem(key, value)
  other.removeItem(key)
}

function readPermissions() {
  try {
    const raw = readItem(PERM_KEY)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(readItem(ADMIN_TOKEN_KEY))
  const refreshToken = ref(readItem(ADMIN_REFRESH_KEY))
  const username = ref(readItem(NAME_KEY))
  const roleCode = ref(readItem(ROLE_KEY))
  const permissions = ref<string[]>(readPermissions())

  function setSession(nextToken: string, nextRefresh: string, nextUsername = '', nextRole = '', nextPermissions: string[] = [], remember = true) {
    token.value = nextToken
    refreshToken.value = nextRefresh
    username.value = nextUsername
    roleCode.value = nextRole
    permissions.value = nextPermissions
    const primary = remember ? localStorage : sessionStorage
    const other = remember ? sessionStorage : localStorage
    const entries: Record<string, string> = {
      [ADMIN_TOKEN_KEY]: nextToken,
      [ADMIN_REFRESH_KEY]: nextRefresh,
      [NAME_KEY]: nextUsername,
      [ROLE_KEY]: nextRole,
      [PERM_KEY]: JSON.stringify(nextPermissions),
    }
    for (const key of SESSION_KEYS) {
      primary.setItem(key, entries[key])
      other.removeItem(key)
    }
  }

  function setTokens(nextToken: string, nextRefresh: string) {
    token.value = nextToken
    refreshToken.value = nextRefresh
    writeItem(ADMIN_TOKEN_KEY, nextToken)
    writeItem(ADMIN_REFRESH_KEY, nextRefresh)
  }

  function clear() {
    token.value = ''
    refreshToken.value = ''
    username.value = ''
    roleCode.value = ''
    permissions.value = []
    for (const key of SESSION_KEYS) {
      localStorage.removeItem(key)
      sessionStorage.removeItem(key)
    }
  }

  function has(code: string) {
    return permissions.value.includes(code)
  }

  return { token, refreshToken, username, roleCode, permissions, setSession, setTokens, clear, has }
})
