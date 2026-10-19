import { GLView, type ExpoWebGLRenderingContext } from 'expo-gl';
import { forwardRef, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, PanResponder, StyleSheet, View } from 'react-native';
import type { LngLat } from '../domain/geo';
import { Txt, useTheme } from '../ui/theme';
import { createGlobeScene, type CityLabel, type GlobeData } from './scene';

export type GlobeHandle = { focus: (a: LngLat, b: LngLat) => void; stopIdle: () => void };
type Props = {
  data: GlobeData;
  selectedId?: string;
  center: LngLat;
  onCity: (iata: string) => void;
  onFlight: (id: string) => void;
  onClear: () => void;
};

export const GlobeView = forwardRef<GlobeHandle, Props>(function GlobeView(props, ref) {
  const { c } = useTheme();
  const current = useRef({ ...props, c });
  current.current = { ...props, c };
  const scene = useRef<ReturnType<typeof createGlobeScene> | null>(null);
  const size = useRef({ width: 1, height: 1 });
  const touched = useRef(false);
  const reduced = useRef(true);
  const active = useRef(AppState.currentState === 'active');
  const mounted = useRef(true);
  const frame = useRef<number | undefined>(undefined);
  const focus = useRef<[LngLat, LngLat] | undefined>(undefined);
  const [labels, setLabels] = useState<CityLabel[]>([]);
  const [error, setError] = useState(false);

  useImperativeHandle(ref, () => ({
    stopIdle() { touched.current = true; },
    focus(a, b) {
      touched.current = true;
      focus.current = [a, b];
      scene.current?.focus(a, b);
    },
  }), []);

  useEffect(() => {
    mounted.current = true;
    let cancelled = false;
    AccessibilityInfo.isReduceMotionEnabled().then((value) => { if (!cancelled) reduced.current = value; }).catch(() => {});
    const motion = AccessibilityInfo.addEventListener('reduceMotionChanged', (value) => { reduced.current = value; });
    const state = AppState.addEventListener('change', (value) => { active.current = value === 'active'; });
    return () => {
      cancelled = true;
      mounted.current = false;
      motion.remove(); state.remove();
      if (frame.current !== undefined) cancelAnimationFrame(frame.current);
      scene.current?.dispose();
      scene.current = null;
    };
  }, []);

  useEffect(() => { scene.current?.update(props.data, c, props.selectedId); }, [props.data, props.selectedId, c]);

  const responder = useMemo(() => {
    let lastX = 0, lastY = 0, lastPinch = 0, moved = false;
    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        touched.current = true;
        focus.current = undefined;
        lastX = 0; lastY = 0; lastPinch = 0; moved = false;
      },
      onPanResponderMove: (event, gesture) => {
        const touches = event.nativeEvent.touches;
        if (touches.length >= 2) {
          const distance = Math.hypot(touches[0].pageX - touches[1].pageX, touches[0].pageY - touches[1].pageY);
          if (lastPinch > 0 && distance > 0) scene.current?.zoom(distance / lastPinch);
          lastPinch = distance;
          moved = true;
        } else {
          if (!lastPinch) scene.current?.drag(gesture.dx - lastX, gesture.dy - lastY);
          if (Math.hypot(gesture.dx, gesture.dy) > 5) moved = true;
          lastPinch = 0;
        }
        lastX = gesture.dx; lastY = gesture.dy;
      },
      onPanResponderRelease: (event) => {
        if (moved) return;
        const { locationX, locationY } = event.nativeEvent;
        const hit = scene.current?.pick(locationX, locationY);
        if (hit?.iata) current.current.onCity(hit.iata);
        else if (hit?.id) current.current.onFlight(hit.id);
        else current.current.onClear();
      },
      onPanResponderTerminationRequest: () => true,
    });
  }, []);

  function onContextCreate(gl: ExpoWebGLRenderingContext) {
    if (!mounted.current) return;
    if (frame.current !== undefined) cancelAnimationFrame(frame.current);
    scene.current?.dispose();
    try {
      const runtime = createGlobeScene(gl, current.current.center);
      scene.current = runtime;
      runtime.resize(size.current.width, size.current.height);
      runtime.update(current.current.data, current.current.c, current.current.selectedId);
      if (focus.current) runtime.focus(...focus.current);
      let previous = 0, lastLabels = 0;
      const draw = (time: number) => {
        if (!mounted.current) return;
        const delta = previous ? Math.min((time - previous) / 1000, 0.05) : 0;
        previous = time;
        try {
          if (active.current) {
            runtime.render(delta, !touched.current && !reduced.current, reduced.current);
            if (time - lastLabels > 100) {
              const next = runtime.labels();
              setLabels((previous) => previous.length === 0 && next.length === 0 ? previous : next);
              lastLabels = time;
            }
          }
          frame.current = requestAnimationFrame(draw);
        } catch (cause) {
          console.warn('Globe rendering failed', cause);
          setError(true);
        }
      };
      frame.current = requestAnimationFrame(draw);
    } catch (cause) {
      console.warn('Globe initialization failed', cause);
      setError(true);
    }
  }

  return (
    <View
      style={StyleSheet.absoluteFill}
      onLayout={({ nativeEvent: { layout } }) => {
        size.current = layout;
        scene.current?.resize(layout.width, layout.height);
        if (focus.current) scene.current?.focus(...focus.current);
      }}
      {...responder.panHandlers}
      accessibilityLabel="Flight globe. Drag to rotate, pinch to zoom. Flights are also available in the list below."
    >
      <GLView style={StyleSheet.absoluteFill} onContextCreate={onContextCreate} />
      <View pointerEvents="none" style={StyleSheet.absoluteFill}>
        {labels.map((label) => (
          <Txt key={label.id} size="caption" muted style={{ position: 'absolute', left: label.x - 60, top: label.y, width: 120, textAlign: 'center' }}>{label.name}</Txt>
        ))}
      </View>
      {error && <View style={[StyleSheet.absoluteFill, { alignItems: 'center', justifyContent: 'center', padding: 32, backgroundColor: c.background }]}><Txt accessibilityRole="alert">The globe could not start. Your flights are still available below.</Txt></View>}
    </View>
  );
});
