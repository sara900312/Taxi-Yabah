import React, { useEffect, useRef, useCallback } from 'react';
import { GeoPoint, placeDisplayName } from '../core/models';
import { Geo, PlaceIndex, Ring } from '../core/rules';

export type MarkerKind = 'Pickup' | 'Dropoff' | 'Captain';
export type RouteStyle = 'ServerRoad' | 'DemoStraight';

export interface MapMarker {
  point: GeoPoint;
  kind: MarkerKind;
}

export interface MapRoute {
  points: GeoPoint[];
  style: RouteStyle;
}

export interface ServiceOverlay {
  outline?: Ring;
  center: GeoPoint;
  radiusM: number;
}

interface DemoMapCanvasProps {
  center: GeoPoint;
  zoom: number;
  markers: MapMarker[];
  route: MapRoute | null;
  overlay: ServiceOverlay | null;
  places: PlaceIndex | null;
  arabic: boolean;
  darkTheme: boolean;
  topPadding: number;
  bottomPadding: number;
  attributionText: string;
  mapDescription: string;
  onCameraMove: (center: GeoPoint, zoom: number, gesturing: boolean) => void;
  onTap: (point: GeoPoint) => void;
}

export const DemoMapCanvas: React.FC<DemoMapCanvasProps> = ({
  center,
  zoom,
  markers,
  route,
  overlay,
  places,
  arabic,
  darkTheme,
  topPadding,
  bottomPadding,
  attributionText,
  mapDescription,
  onCameraMove,
  onTap,
}) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const dragRef = useRef<{
    startX: number;
    startY: number;
    lastX: number;
    lastY: number;
    moved: boolean;
    center: GeoPoint;
    zoom: number;
  } | null>(null);

  const getVisibleCenter = useCallback(
    (w: number, h: number) => {
      const top = topPadding;
      const bottom = Math.min(bottomPadding, h - top - 40);
      return {
        x: w / 2,
        y: top + Math.max(1, h - top - bottom) / 2,
      };
    },
    [topPadding, bottomPadding]
  );

  const projectPoint = useCallback(
    (
      p: GeoPoint,
      camCenter: GeoPoint,
      camZoom: number,
      visCenter: { x: number; y: number }
    ) => {
      const ws = 256.0 * Math.pow(2.0, camZoom);
      const cx = Geo.worldX(camCenter.lng, ws);
      const cy = Geo.worldY(camCenter.lat, ws);
      return {
        x: visCenter.x + (Geo.worldX(p.lng, ws) - cx),
        y: visCenter.y + (Geo.worldY(p.lat, ws) - cy),
      };
    },
    []
  );

  const unprojectPoint = useCallback(
    (
      screen: { x: number; y: number },
      camCenter: GeoPoint,
      camZoom: number,
      visCenter: { x: number; y: number }
    ): GeoPoint => {
      const ws = 256.0 * Math.pow(2.0, camZoom);
      const cx = Geo.worldX(camCenter.lng, ws);
      const cy = Geo.worldY(camCenter.lat, ws);
      return {
        lat: Geo.latAt(cy + (screen.y - visCenter.y), ws),
        lng: Geo.lngAt(cx + (screen.x - visCenter.x), ws),
      };
    },
    []
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    const w = rect.width || 390;
    const h = rect.height || 844;
    if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
    }

    ctx.save();
    ctx.scale(dpr, dpr);

    // Colors from Theme.kt (LightExtras / DarkExtras)
    const mapLand = darkTheme ? '#142135' : '#EDEBE4';
    const mapGrid = darkTheme ? 'rgba(248, 247, 243, 0.10)' : 'rgba(16, 27, 44, 0.08)';
    const mapOutline = '#0DAA9B';
    const mapPlace = darkTheme ? 'rgba(169, 180, 196, 0.50)' : 'rgba(98, 113, 134, 0.40)';
    const mapLabel = darkTheme ? 'rgba(201, 209, 220, 0.85)' : 'rgba(59, 71, 90, 0.85)';
    const watermarkColor = darkTheme
      ? 'rgba(201, 209, 220, 0.05)'
      : 'rgba(59, 71, 90, 0.06)';

    // Background land
    ctx.fillStyle = mapLand;
    ctx.fillRect(0, 0, w, h);

    const vis = getVisibleCenter(w, h);
    const nw = unprojectPoint({ x: 0, y: 0 }, center, zoom, vis);
    const se = unprojectPoint({ x: w, y: h }, center, zoom, vis);

    // 1. Grid
    const step =
      zoom < 9 ? 0.5 : zoom < 11 ? 0.1 : zoom < 13 ? 0.05 : zoom < 15 ? 0.01 : 0.002;
    ctx.strokeStyle = mapGrid;
    ctx.lineWidth = 1;
    ctx.beginPath();
    let lng = Math.floor(nw.lng / step) * step;
    while (lng <= se.lng) {
      const xx = projectPoint({ lat: nw.lat, lng }, center, zoom, vis).x;
      ctx.moveTo(xx, 0);
      ctx.lineTo(xx, h);
      lng += step;
    }
    let lat = Math.ceil(nw.lat / step) * step;
    while (lat >= se.lat) {
      const yy = projectPoint({ lat, lng: nw.lng }, center, zoom, vis).y;
      ctx.moveTo(0, yy);
      ctx.lineTo(w, yy);
      lat -= step;
    }
    ctx.stroke();

    // 2. Governorate outline
    if (overlay?.outline && overlay.outline.length > 2) {
      ctx.beginPath();
      overlay.outline.forEach((pt, idx) => {
        const o = projectPoint({ lat: pt[1], lng: pt[0] }, center, zoom, vis);
        if (idx === 0) ctx.moveTo(o.x, o.y);
        else ctx.lineTo(o.x, o.y);
      });
      ctx.closePath();
      ctx.fillStyle = 'rgba(13, 170, 155, 0.05)';
      ctx.fill();
      ctx.strokeStyle = 'rgba(13, 170, 155, 0.55)';
      ctx.lineWidth = 1.5;
      ctx.lineJoin = 'round';
      ctx.stroke();
    }

    // 3. Service radius circle
    if (overlay) {
      ctx.beginPath();
      for (let i = 0; i <= 72; i++) {
        const dest = Geo.destination(overlay.center, overlay.radiusM, i * 5.0);
        const o = projectPoint(dest, center, zoom, vis);
        if (i === 0) ctx.moveTo(o.x, o.y);
        else ctx.lineTo(o.x, o.y);
      }
      ctx.closePath();
      ctx.fillStyle = 'rgba(13, 170, 155, 0.06)';
      ctx.fill();
      ctx.save();
      ctx.strokeStyle = 'rgba(13, 170, 155, 0.8)';
      ctx.lineWidth = 2;
      ctx.setLineDash([10, 7]);
      ctx.stroke();
      ctx.restore();
    }

    // 4. GeoNames places
    if (places && zoom >= 11.5) {
      const taken: { x1: number; y1: number; x2: number; y2: number }[] = [];
      let labelCount = 0;
      ctx.font = '500 11px "IBM Plex Sans Arabic", sans-serif';
      ctx.textBaseline = 'top';

      for (const pl of places.places) {
        const p = pl.point;
        if (p.lat > nw.lat || p.lat < se.lat || p.lng < nw.lng || p.lng > se.lng) {
          continue;
        }
        const o = projectPoint(p, center, zoom, vis);
        ctx.fillStyle = mapPlace;
        ctx.beginPath();
        ctx.arc(o.x, o.y, 2.2, 0, Math.PI * 2);
        ctx.fill();

        if (zoom >= 12.2 && labelCount < 70) {
          const text = placeDisplayName(pl, arabic);
          const metrics = ctx.measureText(text);
          const tw = Math.min(150, metrics.width);
          const th = 14;
          const tx = o.x - tw / 2;
          const ty = o.y + 4;
          const box = { x1: tx - 4, y1: ty - 2, x2: tx + tw + 4, y2: ty + th + 2 };
          const overlaps = taken.some(
            (b) =>
              !(box.x2 < b.x1 || box.x1 > b.x2 || box.y2 < b.y1 || box.y1 > b.y2)
          );
          if (!overlaps) {
            ctx.fillStyle = mapLabel;
            ctx.fillText(text, tx, ty, 150);
            taken.push(box);
            labelCount++;
          }
        }
      }
    }

    // 5. Route
    if (route && route.points.length >= 2) {
      ctx.beginPath();
      route.points.forEach((p, idx) => {
        const o = projectPoint(p, center, zoom, vis);
        if (idx === 0) ctx.moveTo(o.x, o.y);
        else ctx.lineTo(o.x, o.y);
      });
      if (route.style === 'ServerRoad') {
        ctx.save();
        ctx.strokeStyle = 'rgba(16, 27, 44, 0.85)';
        ctx.lineWidth = 9;
        ctx.lineCap = 'round';
        ctx.lineJoin = 'round';
        ctx.stroke();
        ctx.strokeStyle = mapOutline;
        ctx.lineWidth = 5.5;
        ctx.stroke();
        ctx.restore();
      } else {
        ctx.save();
        ctx.strokeStyle = '#627186';
        ctx.lineWidth = 2.5;
        ctx.lineCap = 'round';
        ctx.setLineDash([6, 6]);
        ctx.stroke();
        ctx.restore();
      }
    }

    // 6. Markers
    for (const m of markers) {
      const o = projectPoint(m.point, center, zoom, vis);
      if (m.kind === 'Pickup') {
        ctx.fillStyle = 'rgba(0, 0, 0, 0.18)';
        ctx.beginPath();
        ctx.arc(o.x, o.y + 2, 13, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#FFFFFF';
        ctx.beginPath();
        ctx.arc(o.x, o.y, 12, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#0DAA9B';
        ctx.beginPath();
        ctx.arc(o.x, o.y, 8.5, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#FFFFFF';
        ctx.beginPath();
        ctx.arc(o.x, o.y, 3, 0, Math.PI * 2);
        ctx.fill();
      } else if (m.kind === 'Dropoff') {
        ctx.fillStyle = 'rgba(0, 0, 0, 0.18)';
        ctx.beginPath();
        ctx.arc(o.x, o.y + 2, 13, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#FFFFFF';
        ctx.beginPath();
        ctx.arc(o.x, o.y, 12, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#E8B65A';
        ctx.beginPath();
        ctx.roundRect(o.x - 6.5, o.y - 6.5, 13, 13, 3);
        ctx.fill();

        ctx.fillStyle = '#101B2C';
        ctx.beginPath();
        ctx.arc(o.x, o.y, 2.5, 0, Math.PI * 2);
        ctx.fill();
      } else if (m.kind === 'Captain') {
        ctx.fillStyle = '#101B2C';
        ctx.beginPath();
        ctx.arc(o.x, o.y, 13, 0, Math.PI * 2);
        ctx.fill();

        ctx.fillStyle = '#E8B65A';
        ctx.beginPath();
        ctx.arc(o.x, o.y, 6, 0, Math.PI * 2);
        ctx.fill();
      }
    }

    // 7. Diagonal DEMO watermark
    ctx.save();
    ctx.translate(w / 2, h / 2);
    ctx.rotate((-24 * Math.PI) / 180);
    ctx.translate(-w / 2, -h / 2);
    ctx.font = '700 64px "IBM Plex Sans Arabic", sans-serif';
    ctx.fillStyle = watermarkColor;
    const wmW = 220;
    const wmH = 85;
    for (let yy = -h * 0.3; yy < h * 1.3; yy += wmH * 2.4) {
      for (let xx = -w * 0.4; xx < w * 1.4; xx += wmW * 1.6) {
        ctx.fillText('DEMO', xx, yy);
      }
    }
    ctx.restore();

    ctx.restore();
  }, [
    center,
    zoom,
    markers,
    route,
    overlay,
    places,
    arabic,
    darkTheme,
    topPadding,
    bottomPadding,
    getVisibleCenter,
    projectPoint,
    unprojectPoint,
  ]);

  const handlePointerDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    e.currentTarget.setPointerCapture(e.pointerId);
    dragRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      lastX: e.clientX,
      lastY: e.clientY,
      moved: false,
      center,
      zoom,
    };
    onCameraMove(center, zoom, true);
  };

  const handlePointerMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const drag = dragRef.current;
    if (!drag) return;
    const dx = e.clientX - drag.startX;
    const dy = e.clientY - drag.startY;
    if (Math.hypot(dx, dy) > 5 || drag.moved) {
      drag.moved = true;
      const canvas = canvasRef.current;
      const w = canvas?.clientWidth || 390;
      const h = canvas?.clientHeight || 844;
      const vis = getVisibleCenter(w, h);
      const nextCenter = unprojectPoint(
        { x: vis.x - dx, y: vis.y - dy },
        drag.center,
        drag.zoom,
        vis
      );
      onCameraMove(nextCenter, drag.zoom, true);
    }
  };

  const handlePointerUp = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const drag = dragRef.current;
    if (!drag) return;
    dragRef.current = null;
    const canvas = canvasRef.current;
    const rect = canvas?.getBoundingClientRect();
    const w = rect?.width || 390;
    const h = rect?.height || 844;
    const vis = getVisibleCenter(w, h);

    if (!drag.moved && rect) {
      const localX = e.clientX - rect.left;
      const localY = e.clientY - rect.top;
      const tapped = unprojectPoint({ x: localX, y: localY }, center, zoom, vis);
      onTap(tapped);
    } else {
      onCameraMove(center, zoom, false);
    }
  };

  const handleWheel = (e: React.WheelEvent<HTMLCanvasElement>) => {
    e.preventDefault();
    const delta = e.deltaY < 0 ? 0.45 : -0.45;
    const nextZoom = Math.max(4, Math.min(18, zoom + delta));
    onCameraMove(center, nextZoom, false);
  };

  return (
    <div className="relative w-full h-full overflow-hidden">
      <canvas
        ref={canvasRef}
        data-testid="demo_map"
        aria-label={mapDescription}
        role="application"
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onWheel={handleWheel}
        onDoubleClick={() => onCameraMove(center, Math.min(18, zoom + 1), false)}
        className="w-full h-full block cursor-grab active:cursor-grabbing touch-none"
      />
      {/* Attribution badge */}
      <div
        style={{ bottom: `${bottomPadding + 8}px` }}
        className="pointer-events-none absolute start-2 z-10 rounded-[6px] bg-white/80 dark:bg-[#18263B]/80 backdrop-blur-xs px-2 py-0.5 text-[11px] leading-[16px] font-medium text-[#627186] dark:text-[#A9B4C4]"
      >
        {attributionText}
      </div>
    </div>
  );
};
