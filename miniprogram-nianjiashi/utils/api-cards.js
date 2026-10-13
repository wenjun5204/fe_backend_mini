/**
 * 契约 API 封装:cards/capsules/visitor tag 14 端点
 * (出入参对齐 api/openapi.yaml v1.7.0 与 .harness/gen/react/src/types/api.ts
 * 的 Template* / Letter* / Card* / Capsule* / Visitor* 类型锚点,字段名以此为准)
 *
 * visitor 组 3 端点免鉴权:noAuth 请求不带 Authorization 头
 * (后端 AuthInterceptor excludePathPatterns 已放行 /api/visitor/**,带 token 也照常 200)
 */
const { request } = require('./request')

// ===== templates tag =====

/** GET /api/templates[?category=] → TemplateListResponse{items}(按 sort 升序) */
function listTemplates(category) {
  return request('/api/templates' + (category ? '?category=' + category : ''))
}

/**
 * POST /api/templates/letter/generate body=LetterGenerateRequest{recipient,stories,tone}
 * → LetterGenerateResponse{generationId,source,paragraphs[{index,role,storyKey,variants,selected}],signText}
 * 每日 5 次频控(40300);source=AI/FALLBACK 前端无感,照常展示
 */
function generateLetter(data) {
  return request('/api/templates/letter/generate', { method: 'POST', data })
}

// ===== cards tag =====

/** GET /api/cards → MadeCardListResponse{items,total,heartsReceived}(本人 ACTIVE,createdAt 降序) */
function listMyCards() {
  return request('/api/cards')
}

/**
 * POST /api/cards body=CreateCardRequest{templateKind,payload,sourceDayId?,capsuleId?}
 * → MadeCard{id,templateKind,payload,totalHearts,...};payload 按 kind 异构,
 * LETTER 强制 styleKey=NIGHT、DAYS_CARD styleKey∈{SUNSET,NIGHT,MINT}、CAPSULE 强制 MINT
 */
function createCard(data) {
  return request('/api/cards', { method: 'POST', data })
}

/** GET /api/cards/{id} → MadeCard(完整 payload;本人否则 40400) */
function getMyCard(id) {
  return request('/api/cards/' + id)
}

/** DELETE /api/cards/{id} → DeleteCardResponse{deleted:true}(软删,幂等) */
function deleteMyCard(id) {
  return request('/api/cards/' + id, { method: 'DELETE' })
}

/** POST /api/cards/{id}/share → ShareSceneResponse{scene,pagePath}(幂等,同卡片恒同码) */
function shareCard(id) {
  return request('/api/cards/' + id + '/share', { method: 'POST' })
}

// ===== capsules tag =====

/**
 * POST /api/capsules body=CapsuleCreateRequest{type,openRule,openDate?,coverHint,contentText,photoUrl?}
 * → TimeCapsule(SEALED 态响应不下发 contentText/photoUrl;ONE_YEAR_LATER 服务端算 openDate=封存日+365)
 */
function sealCapsule(data) {
  return request('/api/capsules', { method: 'POST', data })
}

/** GET /api/capsules → CapsuleListResponse{items,dueCount}(SEALED 倒计时在前,OPENED 回看排后) */
function listMyCapsules() {
  return request('/api/capsules')
}

/** GET /api/capsules/{id} → TimeCapsule(SEALED 只回封面字段+倒计时) */
function getMyCapsule(id) {
  return request('/api/capsules/' + id)
}

/** POST /api/capsules/{id}/open → CapsuleOpenResponse(未到期 40900;幂等,openedAt 不变) */
function openCapsule(id) {
  return request('/api/capsules/' + id + '/open', { method: 'POST' })
}

// ===== visitor tag(免鉴权,零登录依赖) =====

/** GET /api/visitor/cards/{scene}?deviceId= → VisitorCardView{templateKind,publicPayload,lockedTeaser,hearts,unlocked} */
function getVisitorCard(scene, deviceId) {
  return request('/api/visitor/cards/' + scene + '?deviceId=' + deviceId, { noAuth: true })
}

/** POST /api/visitor/cards/{scene}/hearts body={deviceId} → VisitorUnlockState{hearts,unlocked,remainingHint,totalHearts} */
function lightHeart(scene, deviceId) {
  return request('/api/visitor/cards/' + scene + '/hearts', {
    method: 'POST',
    data: { deviceId },
    noAuth: true,
  })
}

/** GET /api/visitor/cards/{scene}/content?deviceId= → UnlockedContentResponse{templateKind,hiddenContent,ctaCopy} */
function getVisitorContent(scene, deviceId) {
  return request('/api/visitor/cards/' + scene + '/content?deviceId=' + deviceId, { noAuth: true })
}

module.exports = {
  listTemplates,
  generateLetter,
  listMyCards,
  createCard,
  getMyCard,
  deleteMyCard,
  shareCard,
  sealCapsule,
  listMyCapsules,
  getMyCapsule,
  openCapsule,
  getVisitorCard,
  lightHeart,
  getVisitorContent,
}
