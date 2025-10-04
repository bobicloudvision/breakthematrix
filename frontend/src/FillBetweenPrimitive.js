/**
 * FillBetweenPrimitive - Fills area between two series/values
 * Supports all Pine Script fill() variations:
 * 1. fill(hline1, hline2, color) - Two horizontal lines
 * 2. fill(plot1, plot2, color) - Two dynamic series
 * 3. fill(plot1, plot2, top_value, bottom_value, top_color, bottom_color) - Gradient fill
 */

class FillBetweenRenderer {
    constructor(source) {
        this._source = source;
    }

    draw(target) {
        target.useBitmapCoordinateSpace(scope => this._source._drawImpl(scope));
    }
}

class FillBetweenPaneView {
    constructor(source) {
        this._source = source;
    }

    update() {
        // Update is called before rendering
    }

    renderer() {
        return new FillBetweenRenderer(this._source);
    }
}

export class FillBetweenPrimitive {
    constructor(chart, series, options = {}) {
        this._chart = chart;
        this._series = series;
        this._options = {
            // Fill mode: 'hline', 'series', 'gradient'
            mode: options.mode || 'series',
            
            // For hline mode
            hline1: options.hline1,
            hline2: options.hline2,
            
            // For series mode (source1/source2 can be 'price', 'open', 'high', 'low', 'close', or array data)
            source1: options.source1 || 'price', // Can be string or array
            source2: options.source2, // Can be string or array
            
            // Colors
            color: options.color || 'rgba(41, 98, 255, 0.1)',
            topColor: options.topColor,
            bottomColor: options.bottomColor,
            
            // Dynamic coloring
            colorMode: options.colorMode || 'static', // 'static', 'dynamic', 'conditional', 'gradient'
            upFillColor: options.upFillColor || 'rgba(76, 175, 80, 0.15)',
            downFillColor: options.downFillColor || 'rgba(239, 83, 80, 0.15)',
            neutralFillColor: options.neutralFillColor || 'rgba(158, 158, 158, 0.1)',
            
            // Additional options
            fillGaps: options.fillGaps !== false,
            display: options.display !== false,
            
            ...options
        };
        
        // Convert source2 array data to map for quick lookup
        this._source1Map = new Map();
        this._source2Map = new Map();
        
        if (Array.isArray(options.source1)) {
            options.source1.forEach(point => {
                this._source1Map.set(point.time, point.value);
            });
        }
        
        if (Array.isArray(options.source2)) {
            console.log(`FillBetweenPrimitive: Loading source2 with ${options.source2.length} points`);
            options.source2.forEach(point => {
                this._source2Map.set(point.time, point.value);
            });
        }
        
        this._paneViews = [new FillBetweenPaneView(this)];
    }

    updateAllViews() {
        this._paneViews.forEach(pv => pv.update());
    }

    paneViews() {
        return this._paneViews;
    }

    _drawImpl(scope) {
        const ctx = scope.context;
        const timeScale = this._chart.timeScale();
        const series = this._series;
        
        if (!series || !this._options.display) return;

        try {
            const visibleRange = timeScale.getVisibleLogicalRange();
            if (!visibleRange) return;
            
            // Handle hline mode (constant horizontal lines)
            if (this._options.mode === 'hline') {
                this._drawHlineFill(scope, timeScale, series, visibleRange);
                return;
            }
            
            // Handle series mode (dynamic data)
            const data = series.data();
            if (!data || data.length === 0) return;

            // Get visible data points
            const from = Math.max(0, Math.floor(visibleRange.from) - 1);
            const to = Math.min(data.length - 1, Math.ceil(visibleRange.to) + 1);

            // Debug logging
            let drawnCount = 0;
            let skippedCount = 0;

            ctx.save();

            // Draw filled areas between two sources
            for (let i = from; i < to; i++) {
                const currentPoint = data[i];
                const nextPoint = data[i + 1];
                
                if (!currentPoint || !nextPoint) {
                    skippedCount++;
                    continue;
                }

                const currentTime = currentPoint.time;
                const nextTime = nextPoint.time;
                
                // Get values from source1
                const currentValue1 = this._getSourceValue(currentPoint, this._options.source1, currentTime, this._source1Map);
                const nextValue1 = this._getSourceValue(nextPoint, this._options.source1, nextTime, this._source1Map);
                
                // Get values from source2
                const currentValue2 = this._getSourceValue(currentPoint, this._options.source2, currentTime, this._source2Map);
                const nextValue2 = this._getSourceValue(nextPoint, this._options.source2, nextTime, this._source2Map);
                
                // Debug first few iterations
                if (i === from && drawnCount === 0) {
                    console.log('FillBetween Debug:', {
                        currentTime,
                        source1Type: typeof this._options.source1,
                        source1Value: this._options.source1,
                        source2Type: typeof this._options.source2,
                        source2MapSize: this._source2Map.size,
                        currentValue1,
                        currentValue2,
                        hasValue1: currentValue1 !== undefined,
                        hasValue2: currentValue2 !== undefined
                    });
                }
                
                if (currentValue1 === undefined || nextValue1 === undefined) {
                    skippedCount++;
                    continue;
                }
                if (currentValue2 === undefined || nextValue2 === undefined) {
                    if (!this._options.fillGaps) {
                        skippedCount++;
                        continue;
                    }
                }

                // Convert to screen coordinates
                const x1 = timeScale.timeToCoordinate(currentTime);
                const x2 = timeScale.timeToCoordinate(nextTime);
                
                if (x1 === null || x2 === null) continue;

                const y1Value1 = series.priceToCoordinate(currentValue1);
                const y1Value2 = series.priceToCoordinate(currentValue2 !== undefined ? currentValue2 : currentValue1);
                const y2Value1 = series.priceToCoordinate(nextValue1);
                const y2Value2 = series.priceToCoordinate(nextValue2 !== undefined ? nextValue2 : nextValue1);
                
                if (y1Value1 === null || y2Value1 === null) continue;
                if (y1Value2 === null || y2Value2 === null) continue;

                // Scale coordinates to bitmap space
                const scaledX1 = x1 * scope.horizontalPixelRatio;
                const scaledX2 = x2 * scope.horizontalPixelRatio;
                const scaledY1Value1 = y1Value1 * scope.verticalPixelRatio;
                const scaledY1Value2 = y1Value2 * scope.verticalPixelRatio;
                const scaledY2Value1 = y2Value1 * scope.verticalPixelRatio;
                const scaledY2Value2 = y2Value2 * scope.verticalPixelRatio;

                // Determine fill color or gradient
                if (this._options.colorMode === 'gradient' && this._options.topColor && this._options.bottomColor) {
                    // Draw with gradient
                    this._drawGradientPolygon(ctx, scope,
                        scaledX1, scaledY1Value1, scaledX2, scaledY2Value1,
                        scaledX1, scaledY1Value2, scaledX2, scaledY2Value2
                    );
                    drawnCount++;
                } else {
                    // Determine solid fill color
                    let fillColor = this._getFillColor(currentValue1, currentValue2, currentPoint, i);
                    
                    // Draw filled polygon
                    ctx.fillStyle = fillColor;
                    ctx.beginPath();
                    ctx.moveTo(scaledX1, scaledY1Value1);
                    ctx.lineTo(scaledX2, scaledY2Value1);
                    ctx.lineTo(scaledX2, scaledY2Value2);
                    ctx.lineTo(scaledX1, scaledY1Value2);
                    ctx.closePath();
                    ctx.fill();
                    drawnCount++;
                }
            }

            ctx.restore();
            
            // Log summary
            if (drawnCount > 0 || skippedCount > 0) {
                console.log(`FillBetween: Drew ${drawnCount} fills, skipped ${skippedCount}`);
            }
        } catch (error) {
            console.error('Error drawing fill between primitive:', error);
        }
    }

    /**
     * Draw fill between two horizontal lines
     * @private
     */
    _drawHlineFill(scope, timeScale, series, visibleRange) {
        const ctx = scope.context;
        const { hline1, hline2 } = this._options;
        
        if (hline1 === undefined || hline2 === undefined) return;

        // Get visible time range
        const leftX = timeScale.logicalToCoordinate(visibleRange.from);
        const rightX = timeScale.logicalToCoordinate(visibleRange.to);
        
        if (leftX === null || rightX === null) return;

        // Convert prices to coordinates
        const y1 = series.priceToCoordinate(hline1);
        const y2 = series.priceToCoordinate(hline2);
        
        if (y1 === null || y2 === null) return;

        // Scale to bitmap space
        const scaledLeftX = leftX * scope.horizontalPixelRatio;
        const scaledRightX = rightX * scope.horizontalPixelRatio;
        const scaledY1 = y1 * scope.verticalPixelRatio;
        const scaledY2 = y2 * scope.verticalPixelRatio;

        ctx.save();
        
        if (this._options.colorMode === 'gradient' && this._options.topColor && this._options.bottomColor) {
            // Create vertical gradient
            const gradient = ctx.createLinearGradient(0, Math.min(scaledY1, scaledY2), 0, Math.max(scaledY1, scaledY2));
            gradient.addColorStop(0, this._options.topColor);
            gradient.addColorStop(1, this._options.bottomColor);
            ctx.fillStyle = gradient;
        } else {
            ctx.fillStyle = this._options.color;
        }
        
        ctx.fillRect(scaledLeftX, scaledY1, scaledRightX - scaledLeftX, scaledY2 - scaledY1);
        ctx.restore();
    }

    /**
     * Get value from source (string key or data map)
     * @private
     */
    _getSourceValue(point, source, time, sourceMap) {
        if (typeof source === 'string') {
            // Source is a price component
            switch (source.toLowerCase()) {
                case 'price':
                case 'close':
                    return point.close !== undefined ? point.close : point.value;
                case 'open':
                    return point.open;
                case 'high':
                    return point.high;
                case 'low':
                    return point.low;
                case 'hl2':
                    return point.high !== undefined && point.low !== undefined ? (point.high + point.low) / 2 : undefined;
                case 'hlc3':
                    return point.high !== undefined && point.low !== undefined && point.close !== undefined 
                        ? (point.high + point.low + point.close) / 3 : undefined;
                case 'ohlc4':
                    return point.open !== undefined && point.high !== undefined && point.low !== undefined && point.close !== undefined
                        ? (point.open + point.high + point.low + point.close) / 4 : undefined;
                default:
                    return point.value;
            }
        } else if (sourceMap.size > 0) {
            // Source is external data
            return sourceMap.get(time);
        }
        return undefined;
    }

    /**
     * Determine fill color based on mode
     * @private
     */
    _getFillColor(value1, value2, point, index) {
        let fillColor = this._options.color;
        
        if (this._options.colorMode === 'dynamic') {
            // Color based on whether value1 is above or below value2
            if (value1 > value2) {
                fillColor = this._options.upFillColor;
            } else if (value1 < value2) {
                fillColor = this._options.downFillColor;
            } else {
                fillColor = this._options.neutralFillColor;
            }
        } else if (this._options.colorMode === 'conditional' && this._options.getColor) {
            // Use custom color function
            fillColor = this._options.getColor(point, value1, value2, index) || fillColor;
        }
        
        return fillColor;
    }

    /**
     * Draw polygon with vertical gradient
     * @private
     */
    _drawGradientPolygon(ctx, scope, x1, y1Top, x2, y2Top, x1b, y1Bottom, x2b, y2Bottom) {
        const minY = Math.min(y1Top, y2Top, y1Bottom, y2Bottom);
        const maxY = Math.max(y1Top, y2Top, y1Bottom, y2Bottom);
        
        const gradient = ctx.createLinearGradient(0, minY, 0, maxY);
        gradient.addColorStop(0, this._options.topColor);
        gradient.addColorStop(1, this._options.bottomColor);
        
        ctx.fillStyle = gradient;
        ctx.beginPath();
        ctx.moveTo(x1, y1Top);
        ctx.lineTo(x2, y2Top);
        ctx.lineTo(x2b, y2Bottom);
        ctx.lineTo(x1b, y1Bottom);
        ctx.closePath();
        ctx.fill();
    }

    /**
     * Update source data (for series mode)
     */
    updateSourceData(source1Data, source2Data) {
        this._source1Map.clear();
        this._source2Map.clear();
        
        if (source1Data && Array.isArray(source1Data)) {
            source1Data.forEach(point => {
                this._source1Map.set(point.time, point.value);
            });
            this._options.source1 = source1Data;
        }
        
        if (source2Data && Array.isArray(source2Data)) {
            source2Data.forEach(point => {
                this._source2Map.set(point.time, point.value);
            });
            this._options.source2 = source2Data;
        }
        
        this.updateAllViews();
    }

    /**
     * Update fill options
     */
    updateOptions(options) {
        this._options = {
            ...this._options,
            ...options
        };
        
        // Update source maps if sources changed
        if (options.source1 && Array.isArray(options.source1)) {
            this._source1Map.clear();
            options.source1.forEach(point => {
                this._source1Map.set(point.time, point.value);
            });
        }
        
        if (options.source2 && Array.isArray(options.source2)) {
            this._source2Map.clear();
            options.source2.forEach(point => {
                this._source2Map.set(point.time, point.value);
            });
        }
        
        this.updateAllViews();
    }
}

