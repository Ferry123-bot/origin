import { defineStore } from 'pinia'
import { ref } from 'vue'

export const PORTAL_TOKEN_KEY = 'yszx_portal_token'
export const PORTAL_REFRESH_KEY = 'yszx_portal_refresh'
const NICKNAME_KEY = 'yszx_portal_nickname'
const AVATAR_KEY = 'yszx_portal_avatar'

function readStored(key: string) {
  return localStorage.getItem(key) || sessionStorage.getItem(key) || ''
}

export function readPortalToken() {
  return readStored(PORTAL_TOKEN_KEY)
}

export function readPortalRefresh() {
  return readStored(PORTAL_REFRESH_KEY)
}

export const useUserStore = defineStore('user', () => {
  const persistent = ref(localStorage.getItem(PORTAL_TOKEN_KEY) != null || sessionStorage.getItem(PORTAL_TOKEN_KEY) == null)
  const token = ref(readStored(PORTAL_TOKEN_KEY))
  const refreshToken = ref(readStored(PORTAL_REFRESH_KEY))
  const nickname = ref(readStored(NICKNAME_KEY))
  const avatar = ref(readStored(AVATAR_KEY))
  const role = ref(token.value ? 'student' : '')

  function box() {
    return persistent.value ? localStorage : sessionStorage
  }

  function write(nextToken: string, nextRefresh: string, nextNickname = nickname.value) {
    const keep = box()
    const drop = persistent.value ? sessionStorage : localStorage
    token.value = nextToken
    refreshToken.value = nextRefresh
    nickname.value = nextNickname
    role.value = nextToken ? 'student' : ''
    keep.setItem(PORTAL_TOKEN_KEY, nextToken)
    keep.setItem(PORTAL_REFRESH_KEY, nextRefresh)
    keep.setItem(NICKNAME_KEY, nextNickname)
    if (avatar.value) keep.setItem(AVATAR_KEY, avatar.value)
    else keep.removeItem(AVATAR_KEY)
    drop.removeItem(PORTAL_TOKEN_KEY)
    drop.removeItem(PORTAL_REFRESH_KEY)
    drop.removeItem(NICKNAME_KEY)
    drop.removeItem(AVATAR_KEY)
  }

  function setSession(nextToken: string, nextRefresh: string, nextNickname = '', remember = true) {
    persistent.value = remember
    write(nextToken, nextRefresh, nextNickname)
  }

  function setTokens(nextToken: string, nextRefresh: string) {
    write(nextToken, nextRefresh)
  }

  function setNickname(nextNickname: string) {
    nickname.value = nextNickname
    box().setItem(NICKNAME_KEY, nextNickname)
  }

  function setAvatar(nextAvatar: string) {
    avatar.value = nextAvatar
    const keep = box()
    const drop = persistent.value ? sessionStorage : localStorage
    if (nextAvatar) keep.setItem(AVATAR_KEY, nextAvatar)
    else keep.removeItem(AVATAR_KEY)
    drop.removeItem(AVATAR_KEY)
  }

  function clear() {
    token.value = ''
    refreshToken.value = ''
    nickname.value = ''
    avatar.value = ''
    role.value = ''
    localStorage.removeItem(PORTAL_TOKEN_KEY)
    localStorage.removeItem(PORTAL_REFRESH_KEY)
    localStorage.removeItem(NICKNAME_KEY)
    localStorage.removeItem(AVATAR_KEY)
    sessionStorage.removeItem(PORTAL_TOKEN_KEY)
    sessionStorage.removeItem(PORTAL_REFRESH_KEY)
    sessionStorage.removeItem(NICKNAME_KEY)
    sessionStorage.removeItem(AVATAR_KEY)
  }

  return { token, refreshToken, nickname, avatar, role, setSession, setTokens, setNickname, setAvatar, clear }
})
