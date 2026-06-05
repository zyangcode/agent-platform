import { describe, expect, it, vi } from 'vitest'
import { apiClient } from '@/lib/api/client'
import {
  createRagDocument,
  deleteRagDocument,
  disableMemory,
  listMemories,
  searchRagDocuments,
  updateMemory,
} from './api'

vi.mock('@/lib/api/client', () => ({
  apiClient: {
    delete: vi.fn(),
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
  },
}))

describe('tools RAG API', () => {
  it('creates RAG documents through the Web management endpoint', async () => {
    await createRagDocument({
      applicationId: 20001,
      chunkTokenBudget: 300,
      content: 'Avoid noon exercise during high heat.',
      overlapTokens: 40,
      profileId: 50001,
      sourceType: 'MANUAL',
      sourceUri: 'kb://sports/basketball',
      title: 'Basketball Safety',
    })

    expect(apiClient.post).toHaveBeenCalledWith('/rag/documents', {
      applicationId: 20001,
      chunkTokenBudget: 300,
      content: 'Avoid noon exercise during high heat.',
      overlapTokens: 40,
      profileId: 50001,
      sourceType: 'MANUAL',
      sourceUri: 'kb://sports/basketball',
      title: 'Basketball Safety',
    })
  })

  it('searches RAG documents with scope query parameters', async () => {
    await searchRagDocuments({
      applicationId: 20001,
      profileId: 50001,
      query: 'basketball heat',
      topK: 3,
    })

    expect(apiClient.get).toHaveBeenCalledWith('/rag/search', {
      query: {
        applicationId: 20001,
        profileId: 50001,
        query: 'basketball heat',
        topK: 3,
      },
    })
  })

  it('deletes RAG documents with scope query parameters', async () => {
    await deleteRagDocument(90001, 20001, 50001)

    expect(apiClient.delete).toHaveBeenCalledWith('/rag/documents/90001', {
      query: {
        applicationId: 20001,
        profileId: 50001,
      },
    })
  })
})

describe('tools memory API', () => {
  it('lists long-term memories with scope and recall filters', async () => {
    await listMemories({
      applicationId: 20001,
      category: 'preference',
      limit: 30,
      profileId: 50001,
      query: 'basketball advice',
    })

    expect(apiClient.get).toHaveBeenCalledWith('/memories', {
      query: {
        applicationId: 20001,
        category: 'preference',
        limit: 30,
        profileId: 50001,
        query: 'basketball advice',
      },
    })
  })

  it('updates editable memory fields through the Web management endpoint', async () => {
    await updateMemory(70001, {
      applicationId: 20001,
      content: 'User prefers concise basketball advice.',
      importance: 0.85,
      memoryCategory: 'preference',
      profileId: 50001,
      slotHint: 'preference',
      tags: ['sports', 'style'],
    })

    expect(apiClient.patch).toHaveBeenCalledWith('/memories/70001', {
      applicationId: 20001,
      content: 'User prefers concise basketball advice.',
      importance: 0.85,
      memoryCategory: 'preference',
      profileId: 50001,
      slotHint: 'preference',
      tags: ['sports', 'style'],
    })
  })

  it('soft-disables long-term memories with scope query parameters', async () => {
    await disableMemory(70001, 20001, 50001)

    expect(apiClient.delete).toHaveBeenCalledWith('/memories/70001', {
      query: {
        applicationId: 20001,
        profileId: 50001,
      },
    })
  })
})
