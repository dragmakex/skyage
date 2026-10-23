import { GLView, type ExpoWebGLRenderingContext } from 'expo-gl';
import { useFocusEffect } from 'expo-router';
import { forwardRef, useCallback, useEffect, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, AppState, PanResponder, StyleSheet, View } from 'react-native';
import type { LngLat } from '../domain/geo';
import { Button } from '../ui/Button';
import { useTheme } from '../ui/theme';
import { GlobePreview } from './GlobePreview';
import { GlobeRenderer } from './renderer';
import type { GlobeData } from './scene';

export type GlobeHandle = { focus: (a: LngLat, b: LngLat) => void; stopIdle: () => void };
type Props = {
  data: GlobeData;
  selectedId?: string;
  center: LngLat;
  onCity: (iata: string) => void;
  onFlight: (id: string) => void;
  onClear: () => void;
};
const isForeground = () => AppState.currentState === null || AppState.currentState === 'active';

export const GlobeView = forwardRef<GlobeHandle, Props>(function GlobeView(props, ref) {
  const { c } = useTheme();
  const current = useRef({ ...props, c });
  current.current = { ...props, c };
  const renderer = useRef<GlobeRenderer | null>(null);
  const touched = useRef(false);
  const reduced = useRef(true);
  const focus = useRef<[LngLat, LngLat] | undefined>(undefined);
  const active = useRef(false);
  const focused = useRef(false);
  const readyRef = useRef(false);
  const attempts = useRef(0);
  const generationRef = useRef(0);
  const retry = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const watchdog = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [generation, setGeneration] = useState(0);
  const [visible, setVisible] = useState(false);
  const [ready, setReady] = useState(false);
  const [recovering, setRecovering] = useState(false);
  const [size, setSize] = useState({ width: 0, height: 0 });
  const sizeRef = useRef(size);

  const stop = useCallback(() => {
    clearTimeout(retry.current); clearTimeout(watchdog.current);
    renderer.current?.stop(); renderer.current = null;
    readyRef.current = false;
  }, []);
  const restart = useCallback(() => {
    stop(); setReady(false);
    generationRef.current += 1;
    setGeneration(generationRef.current);
  }, [stop]);
  const recover = useCallback((cause: unknown) => {
    if (!active.current) return;
    console.warn('Globe surface interrupted; keeping preview visible and recovering', cause);
    stop(); setReady(false); setRecovering(true);
    attempts.current += 1;
    // Avoid a hot retry loop on unsupported hardware; focus/resume or the button retries again.
    if (attempts.current <= 3) retry.current = setTimeout(restart, 500 * 2 ** (attempts.current - 1));
  }, [restart, stop]);

  useFocusEffect(useCallback(() => {
    focused.current = true;
    active.current = isForeground();
    attempts.current = 0;
    restart(); setVisible(active.current);
    return () => {
      focused.current = false; active.current = false;
      stop(); setReady(false); setVisible(false);
    };
  }, [restart, stop]));

  useEffect(() => {
    let cancelled = false;
    AccessibilityInfo.isReduceMotionEnabled().then((value) => { if (!cancelled) reduced.current = value; }).catch(() => {});
    const motion = AccessibilityInfo.addEventListener('reduceMotionChanged', (value) => { reduced.current = value; });
    const app = AppState.addEventListener('change', (value) => {
      active.current = focused.current && value === 'active';
      if (active.current) { attempts.current = 0; restart(); }
      else { stop(); setReady(false); }
      setVisible(active.current);
    });
    return () => { cancelled = true; active.current = false; motion.remove(); app.remove(); stop(); };
  }, [restart, stop]);

  useEffect(() => {
    if (!visible) return;
    watchdog.current = setTimeout(() => { if (!readyRef.current) recover(new Error('Timed out waiting for the globe surface.')); }, 5000);
    return () => clearTimeout(watchdog.current);
  }, [visible, generation, recover]);
  useEffect(() => { renderer.current?.update(props.data, c, props.selectedId); }, [props.data, props.selectedId, c]);

  useImperativeHandle(ref, () => ({
    stopIdle() { touched.current = true; },
    focus(a, b) {
      touched.current = true; focus.current = [a, b];
      renderer.current?.focus(a, b);
    },
  }), []);

  const responder = useMemo(() => {
    let lastX = 0, lastY = 0, lastPinch = 0, moved = false;
    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        touched.current = true; focus.current = undefined;
        lastX = 0; lastY = 0; lastPinch = 0; moved = false;
      },
      onPanResponderMove: (event, gesture) => {
        const touches = event.nativeEvent.touches;
        if (touches.length >= 2) {
          const distance = Math.hypot(touches[0].pageX - touches[1].pageX, touches[0].pageY - touches[1].pageY);
          if (lastPinch > 0 && distance > 0) renderer.current?.zoom(distance / lastPinch);
          lastPinch = distance; moved = true;
        } else {
          if (!lastPinch) renderer.current?.drag(gesture.dx - lastX, gesture.dy - lastY);
          if (Math.hypot(gesture.dx, gesture.dy) > 5) moved = true;
          lastPinch = 0;
        }
        lastX = gesture.dx; lastY = gesture.dy;
      },
      onPanResponderRelease: (event) => {
        if (moved || !readyRef.current) return;
        const { locationX, locationY } = event.nativeEvent;
        const hit = renderer.current?.pick(locationX, locationY);
        if (hit?.iata) current.current.onCity(hit.iata);
        else if (hit?.id) current.current.onFlight(hit.id);
        else current.current.onClear();
      },
      onPanResponderTerminationRequest: () => true,
    });
  }, []);

  function onContextCreate(gl: ExpoWebGLRenderingContext) {
    if (!active.current || generation !== generationRef.current) return;
    renderer.current?.stop();
    const next = new GlobeRenderer(
      () => {
        if (renderer.current !== next || !active.current) return;
        clearTimeout(watchdog.current); clearTimeout(retry.current);
        readyRef.current = true; attempts.current = 0;
        setReady(true); setRecovering(false);
      },
      recover,
      () => ({ idle: !touched.current && !reduced.current, reduced: reduced.current }),
    );
    renderer.current = next;
    next.resize(sizeRef.current.width, sizeRef.current.height);
    next.connect(gl, { center: current.current.center, data: current.current.data, colors: current.current.c, selectedId: current.current.selectedId, focus: focus.current });
  }

  return (
    <View
      style={StyleSheet.absoluteFill}
      onLayout={({ nativeEvent: { layout } }) => {
        sizeRef.current = layout; setSize({ width: layout.width, height: layout.height });
        renderer.current?.resize(layout.width, layout.height);
        if (focus.current) renderer.current?.focus(...focus.current);
      }}
      accessibilityLabel="Flight globe. Drag to rotate, pinch to zoom. Flights are also available in the list below."
    >
      <View style={StyleSheet.absoluteFill} {...responder.panHandlers}>
        {visible && size.width > 0 && size.height > 0 && <GLView key={generation} style={StyleSheet.absoluteFill} onContextCreate={onContextCreate} />}
        {!ready && (
          <View pointerEvents="none" style={[StyleSheet.absoluteFill, { backgroundColor: c.background }]}>
            <GlobePreview diameter={Math.min(size.width, size.height) * 0.88} />
          </View>
        )}
      </View>
      {!ready && recovering && <View style={{ position: 'absolute', bottom: 8, alignSelf: 'center' }}><Button title="Retry interactive globe" variant="secondary" pill onPress={() => { attempts.current = 0; restart(); }} /></View>}
    </View>
  );
});
