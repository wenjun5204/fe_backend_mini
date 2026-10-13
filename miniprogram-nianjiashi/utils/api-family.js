/**
 * 契约 API 封装:family 空间 v1.8.0 新端点
 * (出入参对齐 api/openapi.yaml v1.8.0 与 .harness/gen/react/src/types/api.ts
 * 的 Festival* / Photo* / Moment* / FamilyMomentView 类型锚点,字段名以此为准)
 *
 * 14 端点归属:
 * - festival 4:本文件 reserveFestival/listMyReservations/cancelReservation/listDueFestivals
 *   (无 GET /api/festivals 清单端点,模板清单走既有 GET /api/templates)
 * - photos 2:uploadPhoto 在 photo.js(压缩链路内调用,不走本文件);
 *   getPhoto 免鉴权回图走 photo.js 的 photoUrl() URL 拼接(<image> 直挂,不经 request())
 * - moments 8:本文件 postMoment/listMoments/deleteMoment/likeMoment/unlikeMoment/
 *   collectMoment/uncollectMoment/listDayMoments(素材池,归 moments tag)
 *
 * 末尾另附既有 family tag 3 端点(getMyFamily/createFamily/joinFamily)的转发封装,
 * 家人 Tab 引导建家/进家复用,不计入 14 新端点。
 */
const { request } = require('./request')

// ===== festival tag(4)——「上线提醒我」预约登记与到期清单 =====

/** POST /api/festivals/reservations body={templateKey} → FestivalReservation{templateKey,reserved:true,reservedAt}(幂等) */
function reserveFestival(templateKey) {
  return request('/api/festivals/reservations', { method: 'POST', data: { templateKey } })
}

/** GET /api/festivals/reservations → ReservationListResponse{items,total}(reservedAt 降序) */
function listMyReservations() {
  return request('/api/festivals/reservations')
}

/** DELETE /api/festivals/reservations/{templateKey} → CancelReservationResponse{deleted:true}(幂等) */
function cancelReservation(templateKey) {
  return request('/api/festivals/reservations/' + encodeURIComponent(templateKey), { method: 'DELETE' })
}

/** GET /api/festivals/due?date=yyyy-MM-dd(缺省当天) → FestivalDueResponse{items,total}(daysUntil 升序) */
function listDueFestivals(date) {
  return request('/api/festivals/due' + (date ? '?date=' + date : ''))
}

// ===== moments tag(8)——家人瞬间时间线与真好/收藏互动 =====

/**
 * POST /api/moments body=MomentCreateRequest{photoId,text} → FamilyMomentView
 * (photoId 须本人上传且 scene=MOMENT 且未绑定;未加入家族 40900;每日 10 条 40300)
 */
function postMoment(data) {
  return request('/api/moments', { method: 'POST', data })
}

/**
 * GET /api/moments?beforeId=&limit= → MomentListResponse{items,nextCursor?}
 * (createdAt 倒序 cursor 分页,limit 1-20 缺省 10;无家族返回空列表不报错)
 */
function listMoments(beforeId, limit) {
  let qs = ''
  if (beforeId) {
    qs = '?beforeId=' + beforeId
    if (limit) {
      qs += '&limit=' + limit
    }
  } else if (limit) {
    qs = '?limit=' + limit
  }
  return request('/api/moments' + qs)
}

/** DELETE /api/moments/{id} → DeleteMomentResponse{deleted:true}(仅本人,软删联动照片,幂等) */
function deleteMoment(id) {
  return request('/api/moments/' + id, { method: 'DELETE' })
}

/** POST /api/moments/{id}/like → MomentLikeResponse{likeCount,likedByMe:true}(每人每条限 1 次,幂等) */
function likeMoment(id) {
  return request('/api/moments/' + id + '/like', { method: 'POST' })
}

/** DELETE /api/moments/{id}/like → MomentLikeResponse{likeCount,likedByMe:false}(幂等) */
function unlikeMoment(id) {
  return request('/api/moments/' + id + '/like', { method: 'DELETE' })
}

/** POST /api/moments/{id}/collect body={dayId} → MomentCollectResponse{collectedCount,collectedByMe:true,dayId}(dayId 须 BIRTHDAY) */
function collectMoment(id, dayId) {
  return request('/api/moments/' + id + '/collect', { method: 'POST', data: { dayId } })
}

/** DELETE /api/moments/{id}/collect?dayId= → MomentCollectResponse{collectedByMe:false}(幂等) */
function uncollectMoment(id, dayId) {
  return request('/api/moments/' + id + '/collect?dayId=' + dayId, { method: 'DELETE' })
}

/** GET /api/days/{id}/moments → DayCollectedMomentsResponse{items,total}(BIRTHDAY 日子的收藏素材池,collectedAt 倒序) */
function listDayMoments(dayId) {
  return request('/api/days/' + dayId + '/moments')
}

// ===== 既有 family tag(转发,引导建家/进家复用) =====

/** GET /api/family → FamilyDetail{notJoined,familyName,members,...}(未加入时 notJoined=true) */
function getMyFamily() {
  return request('/api/family')
}

/** POST /api/family body={name} → FamilyDetail(每人限 1 个,创建者即户主) */
function createFamily(name) {
  return request('/api/family', { method: 'POST', data: { name } })
}

/** POST /api/family/join body={inviteCode} → FamilyDetail(上限 8 人) */
function joinFamily(inviteCode) {
  return request('/api/family/join', { method: 'POST', data: { inviteCode } })
}

module.exports = {
  reserveFestival,
  listMyReservations,
  cancelReservation,
  listDueFestivals,
  postMoment,
  listMoments,
  deleteMoment,
  likeMoment,
  unlikeMoment,
  collectMoment,
  uncollectMoment,
  listDayMoments,
  getMyFamily,
  createFamily,
  joinFamily,
}
