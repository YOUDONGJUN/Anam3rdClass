import { describe, expect, it } from 'vitest'
import { fitWithin } from './photo'

describe('사진 줄이기 크기', () => {
  it('긴 변을 기준으로 비율을 지켜 줄인다', () => {
    expect(fitWithin(4032, 3024, 1280)).toEqual({ width: 1280, height: 960 })
    expect(fitWithin(3024, 4032, 2048)).toEqual({ width: 1536, height: 2048 })
  })

  it('작은 사진은 그대로', () => {
    expect(fitWithin(800, 600, 1280)).toEqual({ width: 800, height: 600 })
    expect(fitWithin(0, 0, 1280)).toEqual({ width: 0, height: 0 })
  })
})
