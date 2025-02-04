/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useMemo } from 'react';
import { take, isEmpty, last } from 'lodash';
import { Plt } from '../../plotly/plot';
import { AvailabilityUnitType } from '../../../event_analytics/explorer/visualizations/config_panel/config_panes/config_controls/config_availability';
import { ThresholdUnitType } from '../../../event_analytics/explorer/visualizations/config_panel/config_panes/config_controls/config_thresholds';
import { DefaultChartStyles, FILLOPACITY_DIV_FACTOR, PLOTLY_COLOR } from '../../../../../common/constants/shared';
import { hexToRgb } from '../../../../components/event_analytics/utils/utils';

export const Line = ({ visualizations, layout, config }: any) => {
  const { DefaultMode, Interpolation, LineWidth, FillOpacity, MarkerSize, LegendPosition, ShowLegend } = DefaultChartStyles;
  const {
    data = {},
    metadata: { fields },
  } = visualizations.data.rawVizData;
  const { defaultAxes } = visualizations.data;
  const {
    dataConfig = {},
    layoutConfig = {},
    availabilityConfig = {},
  } = visualizations?.data?.userConfigs;

  const dataConfigTab = visualizations.data?.rawVizData?.line?.dataConfig && visualizations.data.rawVizData.line.dataConfig;
  const xaxis = dataConfigTab?.dimensions ? dataConfigTab?.dimensions : [];
  const yaxis = dataConfigTab?.metrics ? dataConfigTab?.metrics : [];

  const lastIndex = fields.length - 1;

  const mode = dataConfig?.chartStyles?.style || DefaultMode;
  const lineShape = dataConfig?.chartStyles?.interpolation || Interpolation;
  const lineWidth = dataConfig?.chartStyles?.lineWidth || LineWidth;
  const showLegend = !(dataConfig?.legend?.showLegend && dataConfig.legend.showLegend !== ShowLegend);
  const legendPosition = dataConfig?.legend?.position || LegendPosition;
  const markerSize = dataConfig?.chartStyles?.pointSize || MarkerSize;
  const fillOpacity = dataConfig?.chartStyles?.fillOpacity !== undefined ? dataConfig?.chartStyles?.fillOpacity / FILLOPACITY_DIV_FACTOR : FillOpacity / FILLOPACITY_DIV_FACTOR;

  let valueSeries;
  if (!isEmpty(xaxis) && !isEmpty(yaxis)) {
    valueSeries = [...yaxis];
  } else {
    valueSeries = defaultAxes.yaxis || take(fields, lastIndex > 0 ? lastIndex : 1);
  }

  let multiMetrics = {};
  const [calculatedLayout, lineValues] = useMemo(() => {
    const isBarMode = mode === 'bar';

    let calculatedLineValues = valueSeries.map((field: any, index: number) => {
      const fillColor = hexToRgb(PLOTLY_COLOR[index % PLOTLY_COLOR.length], fillOpacity);
      const barMarker = {
        color: fillColor,
        line: {
          color: PLOTLY_COLOR[index],
          width: lineWidth
        }
      };
      const fillProperty = {
        fill: 'tozeroy',
        fillcolor: fillColor,
      };
      const multiYaxis = { yaxis: `y${index + 1}` };
      multiMetrics = {
        ...multiMetrics,
        [`yaxis${index + 1}`]: {
          // title: `yaxis${index + 1} title`, TODO: need to add title
          titlefont: { color: PLOTLY_COLOR[index] },
          tickfont: { color: PLOTLY_COLOR[index] },
          overlaying: 'y',
          side: index === 0 ? 'left' : field.side || "right"
        }
      }

      return {
        x: data[!isEmpty(xaxis) ? xaxis[0]?.label : fields[lastIndex].name],
        y: data[field.label],
        type: isBarMode ? 'bar' : 'scatter',
        name: field.label,
        mode,
        ...!['bar', 'markers'].includes(mode) && fillProperty,
        line: {
          shape: lineShape,
          width: lineWidth,
          color: PLOTLY_COLOR[index],
        },
        marker: {
          size: markerSize,
          ...isBarMode && barMarker,
        },
        ...(index >= 1 && multiYaxis)
      };
    });

    let layoutForBarMode = {
      barmode: 'group',
    };
    const mergedLayout = {
      ...layout,
      ...layoutConfig.layout,
      title: dataConfig?.panelOptions?.title || layoutConfig.layout?.title || '',
      legend: {
        ...layout.legend,
        orientation: legendPosition,
      },
      showlegend: showLegend,
      ...isBarMode && layoutForBarMode,
      ...multiMetrics && multiMetrics,
    };

    if (dataConfig.thresholds || availabilityConfig.level) {
      const thresholdTraces = {
        x: [],
        y: [],
        mode: 'text',
        text: [],
      };
      const thresholds = dataConfig.thresholds ? dataConfig.thresholds : [];
      const levels = availabilityConfig.level ? availabilityConfig.level : [];

      const mapToLine = (list: ThresholdUnitType[] | AvailabilityUnitType[], lineStyle: any) => {
        return list.map((thr: ThresholdUnitType) => {
          thresholdTraces.x.push(
            data[!isEmpty(xaxis) ? xaxis[xaxis.length - 1]?.label : fields[lastIndex].name][0]
          );
          thresholdTraces.y.push(thr.value * (1 + 0.06));
          thresholdTraces.text.push(thr.name);
          return {
            type: 'line',
            x0: data[!isEmpty(xaxis) ? xaxis[0]?.label : fields[lastIndex].name][0],
            y0: thr.value,
            x1: last(data[!isEmpty(xaxis) ? xaxis[0]?.label : fields[lastIndex].name]),
            y1: thr.value,
            name: thr.name || '',
            opacity: 0.7,
            line: {
              color: thr.color,
              width: 3,
              ...lineStyle,
            },
          };
        });
      };

      mergedLayout.shapes = [
        ...mapToLine(thresholds, { dash: 'dashdot' }),
        ...mapToLine(levels, {}),
      ];
      calculatedLineValues = [...calculatedLineValues, thresholdTraces];
    }
    return [mergedLayout, calculatedLineValues];
  }, [data, fields, lastIndex, layout, layoutConfig, xaxis, yaxis, mode, valueSeries]);

  const mergedConfigs = {
    ...config,
    ...(layoutConfig.config && layoutConfig.config),
  };

  return <Plt data={lineValues} layout={calculatedLayout} config={mergedConfigs} />;
};
